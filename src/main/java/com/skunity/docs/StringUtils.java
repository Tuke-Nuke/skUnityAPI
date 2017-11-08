package com.skunity.docs;

import java.util.Objects;

/**
 * @author Tuke_Nuke on 29/08/2017
 */
class StringUtils {

	static boolean hasEmptyString(String... strings) {
		if (strings != null)
			for (String str : strings)
				if (str == null || str.isEmpty())
					return true;
		return strings != null;
	}
	static boolean isArrayEmpty(String... strings) {
		if (strings != null)
			for (String string : strings)
				if (!string.isEmpty())
					return false;
		return true;
	}
	static boolean equalsPatterns(String s1, String s2) {
		if (s1 != null)
			s1 = s1.replaceAll("\\{\\{(.+?)\\|(.+?)\\|(.+?)}}", "$3");
		if (s2 != null)
			s2 = s2.replaceAll("\\{\\{(.+?)\\|(.+?)\\|(.+?)}}", "$3");
		return equals(s1, s2);
	}
	static boolean equals(String s1, String s2) {
		if (s1 == null)
			s1 = "";
		if (s2 == null)
			s2 = "";
		return s1.equals(s2);
	}
}
