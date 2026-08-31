package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.lopyluna.create_lnl.register.client.LiftIcons;

import java.util.List;
import java.util.Locale;

public enum LogicOp implements Node.Merge, INamedIconOptions {
    OR(LiftIcons.I_OR),
    NOR(LiftIcons.I_NOR),
    AND(LiftIcons.I_AND),
    NAND(LiftIcons.I_NAND),
    XOR(LiftIcons.I_XOR),
    XNOR(LiftIcons.I_XNOR),
    ADD(LiftIcons.I_ADD),
    SUB(LiftIcons.I_SUB),
    MUL(LiftIcons.I_MUL),
    DIV(LiftIcons.I_DIV),
    AVG(LiftIcons.I_AVG),
    INV(LiftIcons.I_INV),
    MAX(LiftIcons.I_MAX),
    MIN(LiftIcons.I_MIN),
    MEM(LiftIcons.I_MEM);

    public static final LogicOp[] ALL = values();

    public final String id;
    public final AllIcons icon;

    LogicOp(AllIcons icon) {
        this.icon = icon;
        id = name().toLowerCase(Locale.ROOT);
    }

    public boolean ordered() {
        return this == SUB || this == DIV;
    }

    public boolean latching() {
        return this == MEM;
    }

    public LogicOp next(boolean backwards) {
        return ALL[(ordinal() + (backwards ? ALL.length - 1 : 1)) % ALL.length];
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return "create_lnl.logic_op." + id;
    }

    @Override
    public Node.Signal reduce(Node.Channel channel, List<Node.Signal> inputs) {
        return channel.of(switch (this) {
            case OR, MEM -> strongest(inputs);
            case NOR -> channel.max - strongest(inputs);
            case AND -> weakest(inputs);
            case NAND -> channel.max - weakest(inputs);
            case XOR -> odd(inputs) ? strongest(inputs) : 0;
            case XNOR -> channel.max - (odd(inputs) ? strongest(inputs) : 0);
            case ADD -> sum(inputs);
            case SUB -> difference(inputs);
            case MUL -> product(inputs);
            case DIV -> quotient(inputs);
            case AVG -> inputs.isEmpty() ? 0 : sum(inputs) / inputs.size();
            case INV -> channel.max - sum(inputs);
            case MAX -> largest(inputs);
            case MIN -> smallest(inputs);
        });
    }

    private static boolean odd(List<Node.Signal> inputs) {
        var count = 0;
        for (var signal : inputs) if (signal.active()) count++;
        return (count & 1) == 1;
    }

    private static double largest(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var best = inputs.getFirst().value();
        for (var signal : inputs) if (signal.value() > best) best = signal.value();
        return best;
    }

    private static double smallest(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var best = inputs.getFirst().value();
        for (var signal : inputs) if (signal.value() < best) best = signal.value();
        return best;
    }

    private static double strongest(List<Node.Signal> inputs) {
        var best = 0d;
        for (var signal : inputs) if (Math.abs(signal.value()) > Math.abs(best)) best = signal.value();
        return best;
    }

    private static double weakest(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var best = inputs.getFirst().value();
        for (var signal : inputs) if (Math.abs(signal.value()) < Math.abs(best)) best = signal.value();
        return best;
    }

    private static double sum(List<Node.Signal> inputs) {
        var total = 0d;
        for (var signal : inputs) total += signal.value();
        return total;
    }

    private static double difference(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var total = inputs.getFirst().value();
        for (var i = 1; i < inputs.size(); i++) total -= inputs.get(i).value();
        return total;
    }

    private static double product(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var total = inputs.getFirst().value();
        for (var i = 1; i < inputs.size(); i++) total *= inputs.get(i).value();
        return total;
    }

    private static double quotient(List<Node.Signal> inputs) {
        if (inputs.isEmpty()) return 0;
        var total = inputs.getFirst().value();
        for (var i = 1; i < inputs.size(); i++) {
            var divisor = inputs.get(i).value();
            if (divisor == 0) return 0;
            total /= divisor;
        }
        return total;
    }
}
