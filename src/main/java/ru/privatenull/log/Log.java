package ru.privatenull.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Единый логгер pnRtp. Пока модуль зарегистрирован в pnLibrary, сообщения идут в логгер модуля
 * (они попадают в диагностику вместе со стеком); иначе - в запасной логгер сервера. Ничего не бросает.
 */
public final class Log {

    /** Приёмник сообщений: логгер модуля pnLibrary или запасной логгер платформы. */
    public interface Sink {
        void info(String message);

        void success(String message);

        void warning(String message);

        void error(String message, Throwable error);
    }

    private static volatile Sink module;
    private static volatile Sink fallback = julSink(Logger.getLogger("pnRtp"));

    private Log() {
    }

    /** Запасной логгер, пока модуль не зарегистрирован или уже закрыт (логгер плагина). */
    public static void fallback(Logger logger) {
        fallback = julSink(logger);
    }

    /** Подключает логгер модуля; null при закрытии модуля возвращает запасной путь. */
    public static void attach(Sink sink) {
        module = sink;
    }

    public static void info(String message) {
        deliver(sink -> sink.info(message));
    }

    public static void success(String message) {
        deliver(sink -> sink.success(message));
    }

    public static void warning(String message) {
        deliver(sink -> sink.warning(message));
    }

    /** Логгер модуля не принимает исключение в warning, поэтому с исключением пишем как ошибку, чтобы остался стек. */
    public static void warning(String message, Throwable error) {
        if (error == null) warning(message);
        else error(message, error);
    }

    public static void error(String message) {
        error(message, null);
    }

    public static void error(String message, Throwable error) {
        deliver(sink -> sink.error(message, error));
    }

    private static void deliver(java.util.function.Consumer<Sink> action) {
        Sink current = module;
        if (current != null) {
            try {
                action.accept(current);
                return;
            } catch (Throwable ignored) {
                // Модуль уже закрыт - уходим в запасной путь.
            }
        }
        try {
            action.accept(fallback);
        } catch (Throwable ignored) {
            // Логирование никогда не должно ронять плагин.
        }
    }

    private static Sink julSink(Logger logger) {
        return new Sink() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void success(String message) {
                logger.info(message);
            }

            @Override
            public void warning(String message) {
                logger.warning(message);
            }

            @Override
            public void error(String message, Throwable error) {
                if (error == null) logger.severe(message);
                else logger.log(Level.SEVERE, message, error);
            }
        };
    }
}
