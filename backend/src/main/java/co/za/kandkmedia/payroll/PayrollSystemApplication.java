package co.za.kandkmedia.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayrollSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayrollSystemApplication.class, args);
    }
}
