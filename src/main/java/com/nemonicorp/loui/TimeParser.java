package com.nemonicorp.loui;

/**
 * Conversao e formatacao de duracoes.
 *
 * Nao depende de Bukkit — e a unica peca do plugin testavel sem servidor.
 */
public final class TimeParser {

    private TimeParser() {
    }

    /**
     * Converte o argumento de tempo em minutos.
     * Aceita minutos puros ("30") ou sufixo de minutos ("30m"), horas ("2h") ou dias ("2d").
     * Retorna -1 quando o formato e invalido, o valor nao e positivo, ou a
     * multiplicacao estouraria o long.
     */
    public static long parse(String input) {
        if (input == null || input.isEmpty()) return -1;
        String s = input.toLowerCase();
        long multiplier = 1L; // minutos por unidade
        char last = s.charAt(s.length() - 1);
        if (last == 'd') {
            multiplier = 1440L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'h') {
            multiplier = 60L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            multiplier = 1L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1;
        try {
            long value = Long.parseLong(s);
            if (value <= 0) return -1;
            if (value > Long.MAX_VALUE / multiplier) return -1;
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Relogio da bossbar: "Dd HH:MM:SS", "H:MM:SS" ou "MM:SS" conforme a duracao restante. */
    public static String formatClock(long remainingMs) {
        long totalSec = Math.max(0L, remainingMs) / 1000L;
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long min = (totalSec % 3600L) / 60L;
        long sec = totalSec % 60L;
        if (days > 0) return String.format("%dd %02d:%02d:%02d", days, hours, min, sec);
        if (hours > 0) return String.format("%d:%02d:%02d", hours, min, sec);
        return String.format("%02d:%02d", min, sec);
    }

    /** Texto amigavel de duracao em minutos: "2d 5h", "5h 30min", "30min". */
    public static String formatDuration(long minutes) {
        if (minutes <= 0) return "0min";
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append('d');
        if (hours > 0) sb.append(sb.length() > 0 ? " " : "").append(hours).append('h');
        if (mins > 0 || sb.length() == 0) sb.append(sb.length() > 0 ? " " : "").append(mins).append("min");
        return sb.toString();
    }
}
