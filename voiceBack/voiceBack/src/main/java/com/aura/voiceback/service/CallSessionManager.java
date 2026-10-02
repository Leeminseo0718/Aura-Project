package com.aura.voiceback.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CallSessionManager {

    // sessionId -> callerId, calleeId
    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();
    // 방 정보 저장 (REST 요청과 웹소켓 핸드셰이크가 동시에 접근하므로 thread-safe 컬렉션 사용)
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public String createSession(String callerId, String calleeId) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new CallSession(callerId, calleeId));
        return sessionId;
    }

    public void endSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public CallSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public static class CallSession {
        public final String callerId;
        public final String calleeId;

        public CallSession(String callerId, String calleeId) {
            this.callerId = callerId;
            this.calleeId = calleeId;
        }
    }

    public String createRoom(String creatorId, String roomName) {
        String roomId = UUID.randomUUID().toString();
        Room room = new Room(roomId, roomName, ConcurrentHashMap.newKeySet());
        room.getParticipants().add(creatorId);
        rooms.put(roomId, room);
        return roomId;
    }

    public List<Map<String, Object>> listRooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Room room : rooms.values()) {
            list.add(Map.of(
                    "roomId", room.getId(),
                    "roomName", room.getName(),
                    "participants", room.getParticipants().size()
            ));
        }
        return list;
    }

    public boolean joinRoom(String userId, String roomId) {
        // 방 삭제와 동시에 참가해도 꼬이지 않도록 원자적으로 처리
        return rooms.computeIfPresent(roomId, (id, room) -> {
            room.getParticipants().add(userId);
            return room;
        }) != null;
    }

    // 웹소켓 접속 시 해당 방 참가자인지 확인
    public boolean isParticipant(String roomId, String userId) {
        Room room = rooms.get(roomId);
        return room != null && room.getParticipants().contains(userId);
    }

    public static class Room {
        private final String id;
        private final String name;
        private final Set<String> participants;

        public Room(String id, String name, Set<String> participants) {
            this.id = id;
            this.name = name;
            this.participants = participants;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Set<String> getParticipants() { return participants; }
    }

    public boolean leaveRoom(String userId, String roomId) {
        // 마지막 참가자가 나가면 방 삭제 (null 반환 시 맵에서 제거됨)
        Room remaining = rooms.computeIfPresent(roomId, (id, room) -> {
            room.getParticipants().remove(userId);
            return room.getParticipants().isEmpty() ? null : room;
        });
        return remaining != null; // false: 방 삭제됨(또는 원래 없음), true: 방은 여전히 존재
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    // 모든 Room 객체 반환 (프론트에서 room 목록 조회용)
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
}
