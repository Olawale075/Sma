package sms.com.sms.dto;

public class UserGasDetectorDTO {
    private UserDTO user;
    private DetectorDTO gasDetector;


    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public DetectorDTO getGasDetector() {
        return gasDetector;
    }

    public void setGasDetector(DetectorDTO gasDetector) {
        this.gasDetector = gasDetector;
    }
}
