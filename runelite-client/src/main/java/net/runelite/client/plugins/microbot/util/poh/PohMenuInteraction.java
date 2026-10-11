package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public final class PohMenuInteraction {
    public enum Result { CLICKED, TRANSIENT_FAILURE, UNAVAILABLE, CANCELLED }
    private static final ThreadLocal<Result> lastResult = ThreadLocal.withInitial(() -> Result.TRANSIENT_FAILURE);

    private PohMenuInteraction() { }

    public static void resetResult() { lastResult.set(Result.TRANSIENT_FAILURE); }
    public static void markCancelled() { lastResult.set(Result.CANCELLED); }
    public static Result lastResult() { return lastResult.get(); }

    public static boolean select(String destination, BooleanSupplier openMenu, Supplier<Widget[]> roots) {
        Result result = select(
                () -> Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    for (Widget root : roots.get()) {
                        Widget option = findOption(root, destination);
                        if (option != null) return option;
                    }
                    return null;
                }).orElse(null),
                openMenu,
                ready -> sleepUntil(() -> Thread.currentThread().isInterrupted() || ready.getAsBoolean(), 25_000),
                Rs2Widget::clickWidget);
        lastResult.set(result);
        return result == Result.CLICKED;
    }

    static Result select(Supplier<Widget> find, BooleanSupplier open,
            Function<BooleanSupplier, Boolean> await, Predicate<Widget> click) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) return Result.CANCELLED;
            Widget option = find.get();
            if (option == null) {
                if (!open.getAsBoolean()) continue;
                await.apply(() -> find.get() != null);
                if (Thread.currentThread().isInterrupted()) return Result.CANCELLED;
                option = find.get();
                // Do not dispatch a second approach click after a readiness timeout.
                if (option == null) return Result.TRANSIENT_FAILURE;
            }
            if (option.getText() != null && option.getText().contains("<str>")) return Result.UNAVAILABLE;
            if (click.test(option)) return Result.CLICKED;
        }
        return Result.TRANSIENT_FAILURE;
    }

    static Widget findOption(Widget root, String destination) {
        return findOption(root, destination.toLowerCase(Locale.ROOT),
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Widget findOption(Widget widget, String destination, Set<Widget> visited) {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return null;
        String text = widget.getText();
        Rectangle bounds = widget.getBounds();
        if (text != null && Text.removeTags(text).trim().toLowerCase(Locale.ROOT).endsWith(destination)
                && bounds != null && bounds.width > 0 && bounds.height > 0) return widget;
        Widget[][] groups = {widget.getChildren(), widget.getStaticChildren(),
                widget.getDynamicChildren(), widget.getNestedChildren()};
        for (Widget[] group : groups) {
            if (group == null) continue;
            for (Widget child : group) {
                Widget found = findOption(child, destination, visited);
                if (found != null) return found;
            }
        }
        return null;
    }
}
