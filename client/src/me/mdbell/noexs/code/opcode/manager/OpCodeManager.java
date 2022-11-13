package me.mdbell.noexs.code.opcode.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import me.mdbell.noexs.code.EOperation;
import me.mdbell.noexs.code.opcode.AOpCode;
import me.mdbell.noexs.code.opcode.annotation.AOpCodeOperation;

public class OpCodeManager {

    private static Reflections reflections = new Reflections(AOpCode.class.getPackageName());

    private static final Logger logger = LogManager.getLogger(OpCodeManager.class);

    List<OpCodeOperation> ops = new ArrayList<>();

    Map<EOperation, OpCodeOperation> codeOperations = new HashMap<>();

    public static String[] splitLines(String codes) {
        return StringUtils.split(codes, "\n");
    }

    public static List<AOpCode> decodeCheatCode(String[] cheatCodes) {

        List<AOpCode> res = new ArrayList<>();
        OpCodeManager cr = new OpCodeManager();
        cr.init();

        for (String cheatCode : cheatCodes) {
            String cheat = StringUtils.trim(cheatCode);
            EOperation opCode = EOperation.valueFromFragment(cheat);
            OpCodeOperation cro = cr.codeOperations.get(opCode);
            AOpCode obj = cro.readCodeCheat(cheat);
            res.add(obj);
            logger.info("Cheat {} => {}", cheatCode, obj.toString());
        }

        chainOperations(res);

        return res;
    }

    public static void chainOperations(List<AOpCode> operations) {
        AOpCode last = null;

        for (AOpCode operation : operations) {
            operation.setPreviousOperation(last);
            if (last != null) {
                last.setNextOperation(operation);
            }
            last = operation;
        }

    }

    // TODO a supprimer
    public static String encodeCheatCode(AOpCode decodedOperations) {
        List<AOpCode> operations = new ArrayList<>();
        operations.add(decodedOperations);
        return encodeCheatCode(operations).get(0);
    }

    public static List<String> encodeCheatCode(List<AOpCode> decodedOperations) {
        List<String> res = new ArrayList<>();
        OpCodeManager cr = new OpCodeManager();
        cr.init();
        for (AOpCode decodedOperation : decodedOperations) {
            EOperation op = decodedOperation.getOperation();
            OpCodeOperation cro = cr.codeOperations.get(op);
            res.add(cro.buildCodeFromFragments(decodedOperation));
        }

        return res;
    }

    public static String abstractInstructions(List<AOpCode> decodedOperations) {
        String res = "";
        if (!decodedOperations.isEmpty()) {
            res = decodedOperations.get(decodedOperations.size() - 1).abstractInstruction();
        }
        return res;
    }

    public void init() {

        Set<Class<?>> operations = reflections.get(Scanners.TypesAnnotated.with(AOpCodeOperation.class).asClass());
        for (Class<?> cls : operations) {
            Class<? extends AOpCode> clsDecoded = (Class<? extends AOpCode>) cls;
            OpCodeOperation op = OpCodeOperation.fromRevClass(clsDecoded);
            ops.add(op);
            codeOperations.put(op.getOperation(), op);
        }
    }

}