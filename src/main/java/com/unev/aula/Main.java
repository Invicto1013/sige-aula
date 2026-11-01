package com.unev.aula;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {
    private Main() { }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.desdeEntorno(System.getenv());
        var ds = Pool.crear(cfg);
        AulaDao dao = new AulaDao(ds);
        Servidor servidor = new Servidor(cfg, dao);
        servidor.iniciar();
        var limpieza = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "aula-limpieza"); t.setDaemon(true); return t; });
        limpieza.scheduleWithFixedDelay(() -> {
            try { dao.limpiarHuerfanos(); } catch (Exception e) { Logger.getLogger(Main.class.getName()).log(Level.FINE, "Limpieza omitida", e); }
        }, 2, 360, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { servidor.detener(); ds.close(); }));
    }
}
