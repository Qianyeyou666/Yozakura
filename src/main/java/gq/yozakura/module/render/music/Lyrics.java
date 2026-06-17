package gq.yozakura.module.render.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
    private static final Pattern LINE = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d+))?\\](.*)");
    public static final Lyrics EMPTY = new Lyrics(Collections.<Line>emptyList());

    private final List<Line> lines;

    private Lyrics(List<Line> lines) {
        this.lines = lines;
    }

    public static Lyrics parse(String raw, String translated) {
        ArrayList<Line> result = parseLines(raw);
        if (result.isEmpty()) {
            return EMPTY;
        }
        ArrayList<Line> translatedLines = parseLines(translated);
        for (Line line : result) {
            for (Line extra : translatedLines) {
                if (Math.abs(extra.timeMs - line.timeMs) < 120L && extra.text.length() > 0) {
                    line.translation = extra.text;
                    break;
                }
            }
        }
        return new Lyrics(result);
    }

    public Line current(long positionMs) {
        if (lines.isEmpty()) {
            return Line.EMPTY;
        }
        Line current = lines.get(0);
        for (Line line : lines) {
            if (line.timeMs > positionMs) {
                break;
            }
            current = line;
        }
        return current;
    }

    public List<Line> around(long positionMs, int radius) {
        if (lines.isEmpty()) {
            return Collections.singletonList(Line.EMPTY);
        }
        int index = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) {
                index = i;
            } else {
                break;
            }
        }
        int start = Math.max(0, index - radius);
        int end = Math.min(lines.size(), index + radius + 1);
        return lines.subList(start, end);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    private static ArrayList<Line> parseLines(String raw) {
        ArrayList<Line> result = new ArrayList<Line>();
        if (raw == null || raw.length() == 0) {
            return result;
        }
        String[] split = raw.split("\\r?\\n");
        for (String text : split) {
            Matcher matcher = LINE.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            long minutes = longValue(matcher.group(1));
            long seconds = longValue(matcher.group(2));
            String millisText = matcher.group(3);
            long millis = millisText == null ? 0L : longValue((millisText + "000").substring(0, 3));
            String lyric = matcher.group(4) == null ? "" : matcher.group(4).trim();
            if (lyric.length() == 0) {
                continue;
            }
            result.add(new Line(minutes * 60000L + seconds * 1000L + millis, lyric));
        }
        return result;
    }

    private static long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static final class Line {
        public static final Line EMPTY = new Line(0L, "暂无歌词");
        public final long timeMs;
        public final String text;
        public String translation = "";

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }
}
