/*
 * This file is part of jsortfiles.
 * Copyright (C) 2022 teddyxlandlee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package xland.ioutils.jsortfiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main implements Runnable {
    private final Path source, target;
    private final boolean removeAfterSorting, recursive;
    private final ZoneId zoneId;
    private final DateTimeFormatter timeFormatter;

    public static DateTimeFormatter defaultTimeFormatter() {
        return new DateTimeFormatterBuilder()
                .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                .toFormatter();
    }

    /* FORCE YYYY-MM */
    /* Future: support ISO patterns */
    protected void run0() throws IOException {
        Set<Path> files;
        try (Stream<Path> paths = recursive ? Files.walk(source) : Files.list(source)) {
            files = paths.collect(Collectors.toSet());
        }
        Map<String, Set<Path>> m = new HashMap<>();
        for (Path file : files) {
            final ZonedDateTime time = Files.getLastModifiedTime(file).toInstant().atZone(zoneId);
            final String timeId = time.format(timeFormatter);
            putM(m, timeId, file);
        }
        m.forEach((String timeId, Set<Path> ps) -> {
            Map<Path, Path> s2t = new HashMap<>();
            Set<String> occupiedFilenames = new HashSet<>();
            // Check filename
            for (Path p : ps) {
                final String fn = p.getFileName().toString();
                if (occupiedFilenames.add(fn)) {
                    s2t.put(p, target.resolve(fn));
                } else {
                    int i = 1;
                    final int idxDot = fn.lastIndexOf('.');
                    String a = fn, b = null;
                    if (idxDot >= 0) {
                        a = fn.substring(0, idxDot);
                        b = fn.substring(idxDot + 1);
                    }
                    do {
                        StringBuilder sb = new StringBuilder(a)
                                .append('-').append(i++);
                        if (idxDot >= 0)
                            sb.append('.').append(b);
                        final String e = sb.toString();
                        if (occupiedFilenames.add(e)) {
                            s2t.put(p, target.resolve(e));
                            break;
                        }
                    } while (true);
                }
            }
            occupiedFilenames.forEach(s -> {
                try {
                    Files.createDirectory(target.resolve(s));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            s2t.forEach((p1, p2) -> {
                try {
                    if (removeAfterSorting) {
                        Files.move(p1, p2);
                        System.out.printf("%s\n\t-> %s\n", p1, p2);
                    } else {
                        Files.copy(p1, p2);
                        System.out.printf("%s\n\t=> %s\n", p1, p2);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });
    }

    private static <K, V> void putM(Map<K, Set<V>> map, K k, V v) {
        Set<V> vs = map.computeIfAbsent(k, k1 -> new HashSet<>());
        vs.add(v);
    }

    public Main(Path source, Path target, boolean removeAfterSorting, boolean recursive, ZoneId zoneId, DateTimeFormatter timeFormatter) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(timeFormatter, "timeFormatter");
        this.source = source;
        this.target = target;
        this.removeAfterSorting = removeAfterSorting;
        this.recursive = recursive;
        this.zoneId = zoneId;
        this.timeFormatter = timeFormatter;
    }

    public static void main(String[] rawArgs) {
        final List<Arg> args = Arg.parse(rawArgs, MAP);
        final Iterator<Arg> itr = args.iterator();

        // Extra options are ignored
        Path source = null;
        Path target = null;
        Supplier<ZoneId> zoneId = ZoneId::systemDefault;
        DateTimeFormatter timeFormatter = null;

        boolean removeAfterSorting = false;
        boolean recursive = false;

        while (itr.hasNext()) {
            Arg arg = itr.next();
            if (arg.isRaw()) continue;
            switch (arg.getContext()) {
                case "source":
                    arg = itr.next();
                    source = Paths.get(arg.getContext());
                    break;
                case "formatter":
                    arg = itr.next();
                    timeFormatter = DateTimeFormatter.ofPattern(arg.getContext());
                    break;
                case "help":
                    System.out.println(help());
                    System.exit(0);
                    throw new IncompatibleClassChangeError();
                case "move":
                    removeAfterSorting = true;
                    break;
                case "recursive":
                    recursive = true;
                    break;
                case "target":
                    arg = itr.next();
                    target = Paths.get(arg.getContext());
                    break;
                case "version":
                    System.out.println(copyright());
                    System.exit(0);
                    throw new IncompatibleClassChangeError();
                case "timezone":
                    arg = itr.next();
                {
                    String tz = arg.getContext();
                    zoneId = () -> ZoneId.of(tz);
                }
                    break;
            }
        }
        if (source == null) source = Paths.get("");
        if (target == null) target = source;
        if (timeFormatter == null) timeFormatter = defaultTimeFormatter();

        Runnable r = new Main(source ,target, removeAfterSorting, recursive, zoneId.get(), timeFormatter);
        r.run();
    }

    public static String help() {
        return  "Usage: jsortfiles [options]\n" +
                "Options:\n" +
                "\t-d, --source [directory]: the directory to be sorted, defaulting to working directory\n" +
                "\t-f, --formatter [pattern]: ISO time formatter used to identify different groups of files\n" +
                "\t-h, --help: print this help message\n" +
                "\t-m, --move: remove the original files after sorting\n" +
                "\t-r, --recursive: to iterate the source directory recursively\n" +
                "\t-t, --target [directory]: target directory into which sorted files are moved, defaulting to source\n" +
                "\t-v, --version: print software version and copyright information\n" +
                "\t-z, --timezone [tz]: set the time zone ID used to identify file modification time, defaulting to system zone\n";
    }

    public static String copyright() {
        return  "jsortfiles " + VERSION + "\n" +
                "Copyright (C) 2022 teddyxlandlee\n" +
                "\n" +
                "This program is free software: you can redistribute it and/or modify\n" +
                "it under the terms of the GNU Affero General Public License as published\n" +
                "by the Free Software Foundation, either version 3 of the License, or\n" +
                "(at your option) any later version.\n" +
                "\n" +
                "This program is distributed in the hope that it will be useful,\n" +
                "but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" +
                "GNU Affero General Public License for more details.\n" +
                "\n" +
                "You should have received a copy of the GNU Affero General Public License\n" +
                "along with this program.  If not, see <https://www.gnu.org/licenses/>.";
    }

    public static final String VERSION = "1.0";

    private static final Function<Character, String> MAP;
    static {
        HashMap<Character, String> m = new HashMap<>(8);
        m.put('d', "source");
        m.put('f', "formatter");
        m.put('h', "help");
        m.put('m', "move");
        m.put('r', "recursive");
        m.put('t', "target");
        m.put('v', "version");
        m.put('z', "timezone");
        MAP = m::get;
    }

    @Override
    public void run() {
        try {
            run0();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
