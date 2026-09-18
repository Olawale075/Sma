package sms.com.sms.dto;

public class SmsRequest {
    private String to;
    private String from;
    private String sms;
    private String type = "plain"; // fixed
    private String channel = "generic"; // or "dnd"
    private String api_key;

    // Getters
    public String getTo() {
        return to;
    }

    public String getFrom() {
        return from;
    }

    public String getSms() {
        return sms;
    }

    public String getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public String getApi_key() {
        return api_key;
    }

    // Setters
    public void setTo(String to) {
        this.to = to;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setSms(String sms) {
        this.sms = sms;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setApi_key(String api_key) {
        this.api_key = api_key;
    }
}
