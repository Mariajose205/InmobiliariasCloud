package cl.inmobiliarias.wishlist.dto.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen de un grupo de consumidores Kafka.
 */
public class ConsumerGroupInfoResponse {

    private String groupId;

    private String state;

    private int membersCount;

    private List<String> topics = new ArrayList<>();

    public ConsumerGroupInfoResponse() {
    }

    public ConsumerGroupInfoResponse(String groupId, String state, int membersCount, List<String> topics) {
        this.groupId = groupId;
        this.state = state;
        this.membersCount = membersCount;
        this.topics = topics != null ? topics : new ArrayList<>();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getMembersCount() {
        return membersCount;
    }

    public void setMembersCount(int membersCount) {
        this.membersCount = membersCount;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }
}
