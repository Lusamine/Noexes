package me.mdbell.noexs.code.model;

import org.apache.commons.lang3.StringUtils;

public enum Keypad {
	A(0x00000001), B(0x00000002), X(0x00000004), Y(0x00000008), LEFT_STICK_PRESSED(0x00000010),
	RIGHT_STICK_PRESSED(0x00000020), L(0x00000040), R(0x00000080), ZL(0x00000100), ZR(0x00000200), PLUS(0x00000400),
	MINUS(0x00000800), LEFT(0x00001000), UP(0x00002000), RIGHT(0x00004000), DOWN(0x00008000),
	LEFT_STICK_LEFT(0x00010000), LEFT_STICK_UP(0x00020000), LEFT_STICK_RIGHT(0x00040000), LEFT_STICK_DOWN(0x00080000),
	RIGHT_STICK_LEFT(0x00100000), RIGHT_STICK_UP(0x00200000), RIGHT_STICK_RIGHT(0x00400000),
	RIGHT_STICK_DOWN(0x00800000), SL(0x01000000), SR(0x02000000);

	private long keypadMask;

	private Keypad(long keypadMask) {
		this.keypadMask = keypadMask;
	}

	public long getKeypadMask() {
		return keypadMask;
	}

	public static Keypad getKeypad(String keyPad) {
		return Keypad.valueOf(StringUtils.upperCase(keyPad));
	}
}
