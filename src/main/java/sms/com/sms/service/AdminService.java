package sms.com.sms.service;


import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import sms.com.sms.model.Users;
import sms.com.sms.repository.UsersRepository;

import java.util.List;
import java.util.Optional;

@Service

public class AdminService {

    private final UsersRepository usersRepository;
  public AdminService (UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
     
    }
    public Users createUser(Users user) {
        return usersRepository.save(user);
    }

    public Optional<Users> getUserByPhone(String phoneNumber) {
        return usersRepository.findById(phoneNumber);
    }

    public Page<Users> getAllUsers(Pageable pageable) {
        return usersRepository.findAll(pageable);
    }

    public Users updateUser(String phoneNumber, Users updatedUser) {
        return usersRepository.findById(phoneNumber).map(user -> {
            user.setName(updatedUser.getName());
            user.setEmail(updatedUser.getEmail());
            user.setNotificationPreference(updatedUser.getNotificationPreference());
            user.setIsVerified(updatedUser.getIsVerified());
            user.setRole(updatedUser.getRole());
            // only update password if provided
            if (updatedUser.getPassword() != null) {
                user.setPassword(updatedUser.getPassword());
            }
            return usersRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void deleteUser(String phoneNumber) {
        usersRepository.deleteById(phoneNumber);
    }
}
