// package sms.com.sms.controller;

// import lombok.RequiredArgsConstructor;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageRequest;
// import org.springframework.data.domain.Pageable;
// import org.springframework.data.domain.Sort;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;
// import sms.com.sms.model.Users;
// import sms.com.sms.service.AdminService;

// import java.util.List;

// @RestController
// @RequestMapping("/user/admin")
// @RequiredArgsConstructor
// public class AdminController {
// @Autowired
//     private  AdminService usersService;

//     @PostMapping
//     public ResponseEntity<Users> createUser(@RequestBody Users user) {
//         return ResponseEntity.ok(usersService.createUser(user));
//     }

//     @GetMapping("/{phone}")
//     public ResponseEntity<Users> getUser(@PathVariable String phone) {
//         return usersService.getUserByPhone(phone)
//                 .map(ResponseEntity::ok)
//                 .orElse(ResponseEntity.notFound().build());
//     }

//     @GetMapping
//     public ResponseEntity<Page<Users>> getAllUsers(
//         @RequestParam(defaultValue = "0") int page,
//         @RequestParam(defaultValue = "10") int size,
//         @RequestParam(defaultValue = "createDateTime,DESC") String[] sort) {

//     Sort sortOrder = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
//     Pageable pageable = PageRequest.of(page, size, sortOrder);
//         return ResponseEntity.ok(usersService.getAllUsers(pageable));
//     }

//     @PutMapping("/1/{phone}")
//     public ResponseEntity<Users> updateUser(@PathVariable String phone, @RequestBody Users user) {
//         return ResponseEntity.ok(usersService.updateUser(phone, user));
//     }

//     @DeleteMapping("/{phone}")
//     public ResponseEntity<Void> deleteUser(@PathVariable String phone) {
//         usersService.deleteUser(phone);
//         return ResponseEntity.noContent().build();
//     }
// }
