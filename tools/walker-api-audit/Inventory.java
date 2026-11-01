import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;
import javax.xml.stream.*;

/** Parse pinned Git blobs, without annotation processing, dependency resolution or client startup. */
public class Inventory {
    static final String BASE = "runelite-client/src/main/java/net/runelite/client/plugins/microbot/";
    static final String HUB = "src/main/java/net/runelite/client/plugins/microbot/";
    static final Set<String> OWNERS = new HashSet<>(Arrays.asList(
        "Rs2Walker", "Rs2PathApi", "Rs2MiniMap", "ShortestPathPlugin", "Rs2RoutePlanner",
        "Rs2RouteRequest", "Rs2RouteResult", "Rs2RoutePolicy", "Rs2TransportExecutor",
        "Rs2WalkerMovement", "Rs2WalkerDoors", "Rs2WalkerTransports", "Rs2WalkerSceneObjects",
        "Rs2InteractionApproach", "InteractionRequest", "InteractionResult", "WalkerState",
        "Rs2GameObject", "Rs2TileObjectCache", "Rs2TileObjectModel"));

    static class Source extends SimpleJavaFileObject {
        final String path, blob, text;
        Source(String path, String blob, String text) {
            super(URI.create("string:///" + path.replace(" ", "%20")), Kind.SOURCE);
            this.path = path; this.blob = blob; this.text = text;
        }
        public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
    }
    static class Row extends LinkedHashMap<String, String> {
        Row(String kind, Object... pairs) {
            put("kind", kind);
            for (int i = 0; i < pairs.length; i += 2) put(pairs[i].toString(), String.valueOf(pairs[i + 1]));
        }
    }
    static String compact(Object value) { return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim(); }
    static String flags(Set<Modifier> flags) { return flags.stream().map(Object::toString).sorted().reduce((a,b) -> a+","+b).orElse(""); }
    static String simple(String name) {
        String clean = name.replaceAll("<.*>", "");
        return clean.substring(clean.lastIndexOf('.') + 1);
    }
    static List<Row> parse(List<Source> sources, String mode) throws Exception {
        if (sources.isEmpty()) return new ArrayList<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics,
                Arrays.asList("-proc:none", "-source", "17"), null, sources);
            Iterable<? extends CompilationUnitTree> units = task.parse();
            Trees trees = Trees.instance(task);
            List<Row> rows = new ArrayList<>();
            Map<URI, Source> byUri = new HashMap<>();
            for (Source source : sources) byUri.put(source.toUri(), source);
            for (CompilationUnitTree unit : units) {
                Source source = byUri.get(unit.getSourceFile().toUri());
                String pkg = compact(unit.getPackageName());
                if (mode.equals("api")) {
                    for (Tree declaration : unit.getTypeDecls()) if (declaration instanceof ClassTree)
                        type(rows, unit, trees, source, (ClassTree) declaration, pkg, true, false);
                } else {
                    consumers(rows, unit, trees, source, pkg);
                }
            }
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR)
                throw new IllegalStateException("Parse error: " + d);
            return rows;
        }
    }
    static long line(CompilationUnitTree unit, Trees trees, Tree node) {
        return unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, node));
    }
    static void type(List<Row> rows, CompilationUnitTree unit, Trees trees, Source source,
                     ClassTree node, String parent, boolean parentExternal, boolean parentInterface) {
        String owner = parent.isEmpty() ? node.getSimpleName().toString() : parent + "." + node.getSimpleName();
        Set<Modifier> mods = node.getModifiers().getFlags();
        boolean external = parentExternal && (mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.PROTECTED)
            || parentInterface && !mods.contains(Modifier.PRIVATE));
        boolean iface = node.getKind() == Tree.Kind.INTERFACE || node.getKind() == Tree.Kind.ANNOTATION_TYPE;
        rows.add(new Row("type", "owner", owner, "name", node.getSimpleName(), "shape", node.getKind(),
            "flags", flags(mods), "external", external, "annotations", compact(node.getModifiers().getAnnotations()),
            "extends", compact(node.getExtendsClause()), "implements", compact(node.getImplementsClause()),
            "file", source.path, "blob", source.blob, "line", line(unit, trees, node)));
        for (Tree member : node.getMembers()) {
            if (member instanceof ClassTree) {
                type(rows, unit, trees, source, (ClassTree) member, owner, external, iface);
            } else if (member instanceof MethodTree) {
                MethodTree m = (MethodTree) member;
                Set<Modifier> mf = new HashSet<>(m.getModifiers().getFlags());
                if (iface && !mf.contains(Modifier.PRIVATE)) mf.add(Modifier.PUBLIC);
                String params = m.getParameters().stream().map(p -> compact(p.getType()).replace(" ", ""))
                    .reduce((a,b) -> a+","+b).orElse("");
                boolean constructor = m.getReturnType() == null;
                rows.add(new Row(constructor ? "constructor" : "method", "owner", owner,
                    "name", m.getName(), "params", params, "arity", m.getParameters().size(),
                    "result", compact(m.getReturnType()), "flags", flags(mf),
                    "external", external && (mf.contains(Modifier.PUBLIC) || mf.contains(Modifier.PROTECTED)),
                    "varargs", m.getParameters().stream().anyMatch(p -> p.toString().contains("...")),
                    "throws", compact(m.getThrows()), "typeParameters", compact(m.getTypeParameters()),
                    "annotations", compact(m.getModifiers().getAnnotations()), "file", source.path,
                    "blob", source.blob, "line", line(unit, trees, m)));
            } else if (member instanceof VariableTree) {
                VariableTree field = (VariableTree) member;
                Set<Modifier> ff = new HashSet<>(field.getModifiers().getFlags());
                if (iface) ff.addAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL));
                boolean constant = node.getKind() == Tree.Kind.ENUM && field.getInitializer() instanceof NewClassTree;
                if (constant) ff.addAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL));
                rows.add(new Row(constant ? "enum-constant" : "field", "owner", owner, "name", field.getName(),
                    "result", compact(field.getType()), "flags", flags(ff),
                    "external", external && (ff.contains(Modifier.PUBLIC) || ff.contains(Modifier.PROTECTED)),
                    "annotations", compact(field.getModifiers().getAnnotations()), "file", source.path,
                    "blob", source.blob, "line", line(unit, trees, field)));
            }
        }
    }
    static void consumers(List<Row> rows, CompilationUnitTree unit, Trees trees, Source source, String pkg) {
        Map<String, String> imports = new HashMap<>(), statics = new HashMap<>();
        List<String> staticWildcards = new ArrayList<>();
        for (ImportTree imp : unit.getImports()) {
            String full = imp.getQualifiedIdentifier().toString();
            String owner = imp.isStatic() ? full.substring(0, full.lastIndexOf('.')) : full;
            if (!imp.isStatic()) imports.put(simple(full), full);
            else if (full.endsWith(".*")) staticWildcards.add(owner);
            else statics.put(simple(full), owner);
            if (OWNERS.contains(simple(owner)) || full.contains("microbot.util.walker") || full.contains("microbot.api.tileobject"))
                rows.add(new Row("import", "owner", owner, "name", simple(full), "static", imp.isStatic(),
                    "file", source.path, "blob", source.blob, "line", line(unit, trees, imp)));
        }
        new TreeScanner<Void, Void>() {
            void emit(String kind, Tree tree, String expression, String name, int arity) {
                if (expression.isEmpty() && !statics.containsKey(name)) {
                    for (String candidate : staticWildcards) if (OWNERS.contains(simple(candidate)))
                        rows.add(new Row(kind, "owner", candidate, "name", name, "arity", arity,
                            "binding", "wildcard-static-candidate", "file", source.path, "blob", source.blob,
                            "line", line(unit, trees, tree)));
                    return;
                }
                String resolved = imports.getOrDefault(expression, expression);
                String binding = imports.containsKey(expression) ? "explicit-import" : "qualified-or-unresolved";
                if (expression.isEmpty() && statics.containsKey(name)) { resolved = statics.get(name); binding = "explicit-static-import"; }
                if (!OWNERS.contains(simple(resolved))) return;
                rows.add(new Row(kind, "owner", resolved, "name", name, "arity", arity, "binding", binding,
                    "file", source.path, "blob", source.blob, "line", line(unit, trees, tree)));
            }
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (node.getMethodSelect() instanceof MemberSelectTree) {
                    MemberSelectTree select = (MemberSelectTree) node.getMethodSelect();
                    emit("call", node, select.getExpression().toString(), select.getIdentifier().toString(), node.getArguments().size());
                } else emit("call", node, "", node.getMethodSelect().toString(), node.getArguments().size());
                return super.visitMethodInvocation(node, unused);
            }
            public Void visitMemberReference(MemberReferenceTree node, Void unused) {
                emit("reference", node, node.getQualifierExpression().toString(), node.getName().toString(), -1);
                return super.visitMemberReference(node, unused);
            }
            public Void visitNewClass(NewClassTree node, Void unused) {
                emit("new", node, node.getIdentifier().toString(), "<init>", node.getArguments().size());
                return super.visitNewClass(node, unused);
            }
        }.scan(unit, null);
    }
    static List<String> git(String repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("git", "-c", "safe.directory=" + repo.replace('\\','/'), "-C", repo));
        command.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException("Git failed: " + command);
        return text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split("\\R"));
    }
    static boolean apiPath(String path) {
        if (!path.startsWith(BASE) || !path.endsWith(".java")) return false;
        String tail = path.substring(BASE.length());
        return tail.startsWith("util/walker/") || tail.startsWith("shortestpath/") || tail.startsWith("util/poh/")
            || tail.startsWith("api/tileobject/") || tail.startsWith("util/gameobject/");
    }
    static String header(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b; while ((b = in.read()) != -1 && b != '\n') line.write(b);
        if (b == -1) throw new EOFException("Missing Git object header");
        return line.toString(StandardCharsets.UTF_8);
    }
    static List<Source> load(String repo, String ref, String mode, String diffBase) throws Exception {
        Set<String> changed = diffBase.isEmpty() ? null : new HashSet<>(git(repo, "diff", "--name-only", "--diff-filter=ACMR", diffBase, ref));
        List<String> paths = new ArrayList<>();
        for (String path : git(repo, "ls-tree", "-r", "--name-only", ref)) {
            if (mode.equals("api") ? apiPath(path) : path.endsWith(".java") && (path.startsWith(BASE) || path.startsWith(HUB) || path.startsWith("shared-automation/")))
                if (changed == null || changed.contains(path)) paths.add(path);
        }
        List<Source> sources = new ArrayList<>();
        Process batch = new ProcessBuilder("git", "-c", "safe.directory=" + repo.replace('\\','/'), "-C", repo, "cat-file", "--batch")
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try (OutputStream out = batch.getOutputStream(); BufferedInputStream in = new BufferedInputStream(batch.getInputStream())) {
            for (String path : paths) {
                out.write((ref + ":" + path + "\n").getBytes(StandardCharsets.UTF_8)); out.flush();
                String[] h = header(in).split(" ");
                if (h.length != 3 || !h[1].equals("blob")) throw new IOException("Unexpected Git object: " + Arrays.toString(h));
                int size = Integer.parseInt(h[2]); byte[] bytes = in.readNBytes(size);
                if (bytes.length != size || in.read() != '\n') throw new IOException("Truncated Git blob");
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (mode.equals("api") || OWNERS.stream().anyMatch(text::contains)) sources.add(new Source(path, h[0], text));
            }
        } finally { if (batch.waitFor() != 0) throw new IOException("Git object reader failed"); }
        return sources;
    }
    static void selfTest() throws Exception {
        List<Row> api = parse(Arrays.asList(new Source("Fixture.java", "fixture", "package example; public interface Api { void walk(String... names); } public class Outer { public static final int LIMIT=2; public Outer(){} public class Inner { public boolean ok(){return true;} } private void hidden(){} }")), "api");
        if (api.stream().noneMatch(r -> r.getOrDefault("name", "").equals("walk") && r.get("external").equals("true") && r.get("varargs").equals("true"))
            || api.stream().noneMatch(r -> r.getOrDefault("owner", "").equals("example.Outer.Inner") && r.get("external").equals("true"))
            || api.stream().noneMatch(r -> r.get("kind").equals("constructor"))) throw new AssertionError("API parser fixture failed");
        List<Row> calls = parse(Arrays.asList(new Source("Use.java", "fixture", "package example; import net.runelite.client.plugins.microbot.util.walker.Rs2Walker; import static net.runelite.client.plugins.microbot.util.walker.Rs2PathApi.exit; class Use { void f(){Rs2Walker.walkTo(null);exit();Runnable r=Rs2Walker::setTarget;} }")), "consumers");
        if (calls.stream().filter(r -> r.get("kind").equals("call")).count() != 2 || calls.stream().noneMatch(r -> r.get("kind").equals("reference")))
            throw new AssertionError("Consumer parser fixture failed");
        System.out.println("SELF_TEST_PASS: implicit interface visibility, varargs, nested types, constructors, static imports and method references");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
        if (args.length < 4) throw new IllegalArgumentException("repo commit api|consumers output.xml [changed-from-commit]");
        String repo = args[0], ref = git(repo, "rev-parse", args[1] + "^{commit}").get(0), mode = args[2];
        if (!mode.equals("api") && !mode.equals("consumers")) throw new IllegalArgumentException("Unknown mode");
        List<Source> sources = load(repo, ref, mode, args.length > 4 ? args[4] : "");
        List<Row> rows = parse(sources, mode);
        Path output = Path.of(args[3]); Files.createDirectories(output.toAbsolutePath().getParent());
        try (OutputStream stream = Files.newOutputStream(output)) {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(stream, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("inventory");
            xml.writeAttribute("schema", "1"); xml.writeAttribute("mode", mode); xml.writeAttribute("commit", ref);
            xml.writeAttribute("parsedFiles", String.valueOf(sources.size()));
            for (Row row : rows) { xml.writeStartElement("row"); for (Map.Entry<String,String> e : row.entrySet()) xml.writeAttribute(e.getKey(), e.getValue()); xml.writeEndElement(); }
            xml.writeEndElement(); xml.writeEndDocument(); xml.close();
        }
        System.out.println(mode + " " + ref + ": " + sources.size() + " parsed files, " + rows.size() + " records");
    }
}
