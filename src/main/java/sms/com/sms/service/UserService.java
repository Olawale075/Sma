package sms.com.sms.service;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

// import sms.com.sms.dto.UserDTO;
// import sms.com.sms.dto.UserDTO;
import sms.com.sms.dto.RegisterRequest;
import sms.com.sms.dto.UserDTO;
import sms.com.sms.model.Users;
import java.util.List;

public interface UserService extends UserDetailsService {
    boolean isPhonenumberRegistered(String phonenumber);
    void saveTempUser(Users user);
    Users findTempUser(String phonenumber);
  ResponseEntity<String>verifyOtpAndCreateUser(RegisterRequest user);
String  deleteUser(String phone) ;
    //List<Users> getAllDetails();
     
}
