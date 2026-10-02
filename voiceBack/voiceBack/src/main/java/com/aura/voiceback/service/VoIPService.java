package com.aura.voiceback.service;

import com.aura.voiceback.websocket.VoIPHandshakeInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoIPService {

    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024; // 약 5초 분량, 넘으면 오래된 프레임부터 버림

    // 방ID -> (세션ID -> WebSocketSession)
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    private byte[] convertPCMToWAV(byte[] pcmBytes, int sampleRate, int channels) {
        int byteRate = sampleRate * channels * 2; // 16bit
        int dataSize = pcmBytes.length;
        int totalSize = 44 + dataSize;

        byte[] wav = new byte[totalSize];

        // RIFF 헤더
        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';
        int chunkSize = totalSize - 8;
        wav[4] = (byte) (chunkSize & 0xff);
        wav[5] = (byte) ((chunkSize >> 8) & 0xff);
        wav[6] = (byte) ((chunkSize >> 16) & 0xff);
        wav[7] = (byte) ((chunkSize >> 24) & 0xff);

        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';

        // fmt subchunk
        wav[12] = 'f';
        wav[13] = 'm';
        wav[14] = 't';
        wav[15] = ' ';
        wav[16] = 16; // PCM subchunk size
        wav[17] = 0;
        wav[18] = 0;
        wav[19] = 0;
        wav[20] = 1; // audio format PCM
        wav[21] = 0;
        wav[22] = (byte) channels;
        wav[23] = 0;
        wav[24] = (byte) (sampleRate & 0xff);
        wav[25] = (byte) ((sampleRate >> 8) & 0xff);
        wav[26] = (byte) ((sampleRate >> 16) & 0xff);
        wav[27] = (byte) ((sampleRate >> 24) & 0xff);
        int byteRateLE = byteRate;
        wav[28] = (byte) (byteRateLE & 0xff);
        wav[29] = (byte) ((byteRateLE >> 8) & 0xff);
        wav[30] = (byte) ((byteRateLE >> 16) & 0xff);
        wav[31] = (byte) ((byteRateLE >> 24) & 0xff);
        short blockAlign = (short) (channels * 2);
        wav[32] = (byte) (blockAlign & 0xff);
        wav[33] = (byte) ((blockAlign >> 8) & 0xff);
        short bitsPerSample = 16;
        wav[34] = (byte) (bitsPerSample & 0xff);
        wav[35] = (byte) ((bitsPerSample >> 8) & 0xff);

        // data subchunk
        wav[36] = 'd';
        wav[37] = 'a';
        wav[38] = 't';
        wav[39] = 'a';
        wav[40] = (byte) (dataSize & 0xff);
        wav[41] = (byte) ((dataSize >> 8) & 0xff);
        wav[42] = (byte) ((dataSize >> 16) & 0xff);
        wav[43] = (byte) ((dataSize >> 24) & 0xff);

        // PCM 데이터 복사
        System.arraycopy(pcmBytes, 0, wav, 44, pcmBytes.length);

        return wav;
    }


    public void registerSession(String roomId, WebSocketSession session) {
        // 같은 세션에 여러 송신자가 동시에 보내도 안전하도록 감싸서 저장 (느린 수신자는 오래된 프레임을 버림)
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        // removeSession이 빈 방을 지우는 것과 겹쳐도 등록이 사라지지 않도록 compute 안에서 추가
        roomSessions.compute(roomId, (id, sessions) -> {
            Map<String, WebSocketSession> target = sessions != null ? sessions : new ConcurrentHashMap<>();
            target.put(session.getId(), safeSession);
            return target;
        });
    }

    public void removeSession(String roomId, String sessionId) {
        roomSessions.computeIfPresent(roomId, (id, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    // 같은 방의 다른 참가자에게만 중계
    public void forwardAudio(String roomId, String senderSessionId, byte[] audioBytes) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        // 예: 48000Hz, mono
        byte[] wavBytes = convertPCMToWAV(audioBytes, 48000, 1);

        sessions.forEach((sessionId, s) -> {
            try {
                if (s.isOpen() && !sessionId.equals(senderSessionId)) {
                    s.sendMessage(new BinaryMessage(wavBytes));
                }
            } catch (SessionLimitExceededException e) {
                // 전송이 너무 오래 막힌 수신자는 연결을 끊어 클라이언트가 재접속하도록 함
                try {
                    s.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 방에서 나간 사용자의 웹소켓 연결 종료
    public void closeUserSessions(String roomId, String email) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        sessions.values().forEach(s -> {
            if (email.equals(s.getAttributes().get(VoIPHandshakeInterceptor.ATTR_EMAIL))) {
                try {
                    s.close(CloseStatus.NORMAL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}