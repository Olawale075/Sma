package sms.com.sms;

import org.springframework.boot.SpringApplication;

public class TestSmsApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmsApplication::main).with().run(args);
	}

}
