package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.model.sql.ParameterDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses routine (function/procedure) signatures from SQL declaration text.
 * Extracts name, schema, parameters, and return type.
 */
final class RoutineSignatureParser {

    private RoutineSignatureParser() {
        // utility class
    }

    static RoutineSignature parse(String decl, boolean isFunction) {
        if (decl == null) return null;
        String s = decl.trim();

        Pattern headPat = Pattern.compile("(?is)\\bcreate\\b\\s+(?:or\\s+replace\\s+)?"
                + (isFunction ? "function" : "procedure")
                + "\\s+([\\[\\]`\"\\w\\.]+)");
        Matcher m = headPat.matcher(s);
        if (!m.find()) return null;

        RoutineSignature sig = new RoutineSignature();

        String ident = m.group(1);
        String[] sn = RoutineSqlUtils.splitSchemaAndName(ident);
        sig.schema = sn[0];
        sig.name = sn[1];

        int startParams = s.indexOf('(', m.end());
        if (startParams >= 0) {
            int endParams = SqlRoutineTextUtils.findMatchingParen(s, startParams);
            if (endParams > startParams) {
                String paramsText = s.substring(startParams + 1, endParams).trim();
                if (!paramsText.isEmpty()) {
                    for (String p : SqlRoutineTextUtils.splitTopLevel(paramsText, ',')) {
                        sig.parameters.add(parseParameter(p));
                    }
                }
            }
        }

        if (isFunction) {
            sig.returnType = extractReturnType(s);
        }

        return sig;
    }

    private static ParameterDefinition parseParameter(String raw) {
        String pt = raw.trim();
        ParameterDefinition pd = new ParameterDefinition();

        String lower = pt.toLowerCase();
        if (lower.startsWith("out ")) { pd.direction = "OUT"; pt = pt.substring(4).trim(); }
        else if (lower.startsWith("inout ")) { pd.direction = "INOUT"; pt = pt.substring(6).trim(); }
        else if (lower.startsWith("in out ")) { pd.direction = "INOUT"; pt = pt.substring(6).trim(); }
        else if (lower.startsWith("in ")) { pd.direction = "IN"; pt = pt.substring(3).trim(); }

        String[] toks = pt.split("\\s+", 2);
        if (toks.length >= 1) pd.name = RoutineSqlUtils.stripQuotes(toks[0]);
        if (toks.length >= 2) pd.type = RoutineSqlUtils.normalizeTypeFormat(toks[1].trim());
        return pd;
    }

    private static String extractReturnType(String s) {
        Pattern retPat = Pattern.compile("(?is)\\breturns\\b\\s+([^\\s]+(?:\\s*\\([^\\)]*\\))?)");
        Matcher rm = retPat.matcher(s);
        if (rm.find()) {
            return RoutineSqlUtils.normalizeTypeFormat(rm.group(1).trim());
        }
        Pattern ret2 = Pattern.compile("(?is)\\breturn\\b\\s+([^\\s]+(?:\\s*\\([^\\)]*\\))?)");
        Matcher rm2 = ret2.matcher(s);
        if (rm2.find()) {
            return RoutineSqlUtils.normalizeTypeFormat(rm2.group(1).trim());
        }
        return null;
    }

    static class RoutineSignature {
        String schema;
        String name;
        List<ParameterDefinition> parameters = new ArrayList<>();
        String returnType;
    }
}
