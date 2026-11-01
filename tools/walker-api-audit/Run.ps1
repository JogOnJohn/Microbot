param([Parameter(Mandatory=$true)][string]$WorkspaceFile)
$ErrorActionPreference='Stop'
$w=Get-Content -LiteralPath $WorkspaceFile -Raw|ConvertFrom-Json
$java=Join-Path $w.jdk17 'bin\java.exe'
$tool=Join-Path $PSScriptRoot 'Inventory.java'
$clientReport=Join-Path $w.clientPath 'docs\walker-api-synthesis\inventory'
$hubReport=Join-Path $w.hubPath 'docs\walker-api-synthesis\inventory'
foreach($p in @($w.evidencePath,$clientReport,$hubReport)){New-Item -ItemType Directory -Force $p|Out-Null}
function RunGit($repo,$list){$r=& git -c ('safe.directory='+$repo.Replace('\','/')) -C $repo @list;if($LASTEXITCODE){throw "Git failed: $list"};$r}
function ReadRows($file){
 $xml=[xml][IO.File]::ReadAllText($file)
 foreach($n in @($xml.inventory.row)){
  if(!$n){continue}
  $p=[ordered]@{}
  foreach($attribute in $n.Attributes){$p[$attribute.Name]=$attribute.Value}
  [pscustomobject]$p
 }
}
function Shape($r){(@('result','flags','varargs','throws','typeParameters','shape','extends','implements','annotations')|ForEach-Object {[string]$r.$_}) -join '|'}
function Key($r){"$($r.kind):$($r.owner):$($r.name)($($r.params))"}
function CompareApi($left,$right,$name){
 $l=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 $r=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($x in @($left|Where-Object external -eq 'true')){$l[(Key $x)]=$x}
 foreach($x in @($right|Where-Object external -eq 'true')){$r[(Key $x)]=$x}
 $keys=[Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
 foreach($k in $l.Keys){[void]$keys.Add($k)};foreach($k in $r.Keys){[void]$keys.Add($k)}
 $out=@(foreach($k in $keys){
  $a=$null;$b=$null;[void]$l.TryGetValue($k,[ref]$a);[void]$r.TryGetValue($k,[ref]$b)
  $status=if(!$a){'right-only'}elseif(!$b){'left-only'}elseif((Shape $a) -ceq (Shape $b)){'declaration-shape-match'}else{'declaration-difference'}
  $x=if($a){$a}else{$b}
  [pscustomobject]@{status=$status;kind=$x.kind;owner=$x.owner;name=$x.name;params=$x.params;leftResult=$a.result;rightResult=$b.result;leftFlags=$a.flags;rightFlags=$b.flags;leftFile=$a.file;leftLine=$a.line;rightFile=$b.file;rightLine=$b.line}
 })
 $out|Export-Csv (Join-Path $clientReport ($name+'.csv')) -NoTypeInformation -Encoding UTF8
 [pscustomobject]@{comparison=$name;counts=@($out|Group-Object status|Select-Object Name,Count)}
}
& $java $tool --self-test|Tee-Object -FilePath (Join-Path $w.evidencePath 'parser-self-test.log')
if($LASTEXITCODE){throw 'Parser self-test failed'}
$api=@{};$summary=@()
foreach($v in $w.apiVariants){
 $resolved=RunGit $w.clientRepo @('rev-parse',($v.commit+'^{commit}'))
 if($resolved -cne $v.commit){throw 'API source is not pinned to a full commit'}
 $xml=Join-Path $w.evidencePath ($v.name+'-api.xml')
 & $java -Xmx1g $tool $w.clientRepo $v.commit api $xml
 if($LASTEXITCODE){throw "Inventory failed: $($v.name)"}
 $rows=@(ReadRows $xml);$api[$v.name]=$rows
 $rows|Select-Object kind,owner,name,shape,params,arity,result,flags,external,varargs,throws,typeParameters,annotations,extends,implements,file,line,blob|Export-Csv (Join-Path $clientReport ($v.name+'-api.csv')) -NoTypeInformation -Encoding UTF8
 $meta=[xml][IO.File]::ReadAllText($xml)
 $summary+=[pscustomobject]@{name=$v.name;commit=$v.commit;parsedFiles=[int]$meta.inventory.parsedFiles;externalTypes=@($rows|Where-Object {$_.kind -eq 'type' -and $_.external -eq 'true'}).Count;externalMethods=@($rows|Where-Object {$_.kind -eq 'method' -and $_.external -eq 'true'}).Count;externalFields=@($rows|Where-Object {$_.kind -in @('field','enum-constant') -and $_.external -eq 'true'}).Count}
}
$comparisons=@(foreach($name in @('upstream-development','upstream-main','runtime','data-sync')){CompareApi $api.playable $api[$name] ('playable-vs-'+$name)})
$consumerRows=@()
foreach($v in $w.consumerVariants){
 $repo=if($v.repo -eq 'client'){$w.clientRepo}else{$w.hubRepo}
 $xml=Join-Path $w.evidencePath ($v.name+'-consumers.xml')
 $argsList=@('-Xmx1g',$tool,$repo,$v.commit,'consumers',$xml)
 if($v.changedFrom){$argsList+=@($v.changedFrom)}
 & $java @argsList
 if($LASTEXITCODE){throw "Consumer scan failed: $($v.name)"}
 foreach($row in @(ReadRows $xml)){
  $row|Add-Member -NotePropertyName variant -NotePropertyValue $v.name
  $row|Add-Member -NotePropertyName commit -NotePropertyValue $v.commit
  $consumerRows+=$row
 }
}
$consumerRows|Select-Object variant,commit,kind,owner,name,arity,binding,static,file,line,blob|Export-Csv (Join-Path $hubReport 'consumers.csv') -NoTypeInformation -Encoding UTF8
$calls=@($consumerRows|Where-Object {$_.kind -in @('call','reference','new')})
$lookups=@{}
foreach($name in @('playable','runtime','data-sync','upstream-development')){
 $index=@{}
 foreach($part in @('types','simple','methods')){$index[$part]=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)}
 foreach($row in @($api[$name]|Where-Object external -eq 'true')){
  if($row.kind -eq 'type'){
   $index.types[$row.owner]=$row
   if(!$index.simple.ContainsKey($row.name)){$index.simple[$row.name]=[Collections.Generic.List[object]]::new()}
   $index.simple[$row.name].Add($row)
  }elseif($row.kind -in @('method','constructor')){
   $key=$row.owner+'#'+$row.name
   if(!$index.methods.ContainsKey($key)){$index.methods[$key]=[Collections.Generic.List[object]]::new()}
   $index.methods[$key].Add($row)
  }
 }
 $lookups[$name]=$index
}
$availability=@(foreach($c in $calls){
 $p=[ordered]@{variant=$c.variant;owner=$c.owner;name=$c.name;arity=$c.arity;binding=$c.binding;file=$c.file;line=$c.line}
 foreach($name in @('playable','runtime','data-sync','upstream-development')){
  $index=$lookups[$name];$types=@();$methods=@()
  if($index.types.ContainsKey($c.owner)){$types=@($index.types[$c.owner])}
  elseif($c.owner -notmatch '\.' -and $index.simple.ContainsKey($c.owner)){$types=@($index.simple[$c.owner].ToArray())}
  foreach($type in $types){
   $key=$type.owner+'#'+$c.name
   if($index.methods.ContainsKey($key)){$methods+=@($index.methods[$key]|Where-Object {[int]$c.arity -lt 0 -or [int]$_.arity -eq [int]$c.arity -or $_.varargs -eq 'true' -and [int]$c.arity -ge ([int]$_.arity-1)})}
  }
  $p[$name]=if(!$types.Count){'type-not-in-scoped-inventory'}elseif(!$methods.Count){'not-source-declared-at-arity'}else{'candidate-present:'+ $methods.Count}
 }
 [pscustomobject]$p
})
$availability|Export-Csv (Join-Path $hubReport 'call-availability.csv') -NoTypeInformation -Encoding UTF8
$calls|Group-Object variant,owner,name,arity|ForEach-Object {
 $first=$_.Group[0]
 [pscustomobject]@{variant=$first.variant;owner=$first.owner;name=$first.name;arity=$first.arity;occurrences=$_.Count;files=@($_.Group.file|Sort-Object -Unique).Count}
}|Sort-Object variant,@{Expression='occurrences';Descending=$true}|Export-Csv (Join-Path $hubReport 'call-summary.csv') -NoTypeInformation -Encoding UTF8
[pscustomobject]@{schema=1;scope='Explicit source declarations and lexical static-owner consumers; not binary or semantic compatibility';variants=$summary;comparisons=$comparisons;consumerRecords=$consumerRows.Count;consumerCalls=$calls.Count}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $clientReport 'summary.json') -Encoding UTF8
$summary|Format-Table name,parsedFiles,externalTypes,externalMethods,externalFields
Write-Output 'INVENTORY_COMPLETE'
