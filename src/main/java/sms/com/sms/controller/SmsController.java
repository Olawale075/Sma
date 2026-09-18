package sms.com.sms.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import sms.com.sms.service.SmsService;

@RestController
@RequestMapping("user/api/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send")
    public String sendSms(@RequestParam String to, @RequestParam String message) {
        return smsService.sendSms(to, message);
    }
}
