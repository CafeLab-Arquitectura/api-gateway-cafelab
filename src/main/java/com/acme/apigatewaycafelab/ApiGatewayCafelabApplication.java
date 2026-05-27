package com.acme.apigatewaycafelab;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayCafelabApplication {

    public static void main(String[] args) {
        // Carga el archivo .env antes de que Spring lea su configuración.
        // ignoreIfMissing() hace que en producción (Azure), donde no hay .env,
        // la app arranque normalmente usando las variables del sistema operativo.
        Dotenv.configure()
                .ignoreIfMissing()
                .systemProperties()
                .load();

        SpringApplication.run(ApiGatewayCafelabApplication.class, args);
    }

}
