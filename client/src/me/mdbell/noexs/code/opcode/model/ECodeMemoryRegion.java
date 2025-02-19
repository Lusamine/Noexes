package me.mdbell.noexs.code.opcode.model;

import org.apache.commons.lang3.StringUtils;

import me.mdbell.noexs.code.opcode.annotation.AOpCodeFragmentConversion;
import me.mdbell.noexs.code.opcode.annotation.AOpCodePattern;

/**
 * M: Memory region to write to (0 = Main NSO, 1 = Heap, 2 = Alias, 3 = Aslr).
 * 
 * @author Anthony
 *
 */

@AOpCodePattern(pattern = "[0123]")
public enum ECodeMemoryRegion implements ICodeFragment {
    MAIN(0), HEAP(1), ALIAS(2), ASLR(3);

    private int memoryRegionCode;

    private ECodeMemoryRegion(int memoryRegionCode) {
        this.memoryRegionCode = memoryRegionCode;
    }

    public int getPointerAdressType() {
        return memoryRegionCode;
    }

    public static ECodeMemoryRegion getMemoryRegion(String memoryRegion) {
        return ECodeMemoryRegion.valueOf(StringUtils.upperCase(memoryRegion));
    }

    @AOpCodeFragmentConversion
    public static ECodeMemoryRegion valueFromFragment(String fragment) {
        ECodeMemoryRegion res = null;
        for (ECodeMemoryRegion dt : ECodeMemoryRegion.values()) {
            if (dt.memoryRegionCode == Integer.parseInt(fragment)) {
                res = dt;
                break;
            }
        }
        return res;
    }

    @Override
    public String encode() {
        return Integer.toString(memoryRegionCode);
    }

}
