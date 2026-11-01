package com.unev.aula;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Único lugar que depende de HikariCP. */
final class Pool {
    private Pool() { }

    static HikariDataSource crear(Config c) {
        HikariConfig h = new HikariConfig();
        h.setJdbcUrl(c.dbUrl());
        h.setUsername(c.dbUsuario());
        h.setPassword(c.dbClave());
        h.setMaximumPoolSize(c.tamanoPool());
        h.setMinimumIdle(0);                 // Aiven duerme las conexiones inactivas
        h.setIdleTimeout(60_000);
        h.setMaxLifetime(300_000);
        h.setConnectionTimeout(15_000);
        h.setInitializationFailTimeout(-1);  // arrancar aunque MySQL esté despertando
        h.setPoolName("aula");
        return new HikariDataSource(h);
    }
}
