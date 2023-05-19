package com.assignment.whiteboard.controller;

import com.assignment.whiteboard.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class UserController {
    private String adminName = null;
    private static String VALIDATE_USER_PATH = "/topic/validate-users";
    private static String SHAPE_PATH = "/topic/shape";
    private static String LINE_PATH = "/topic/line";
    private static String TEXT_PATH = "/topic/text";
    private static String CLIENT_CREATE_VALIDATE_PATH = "/queue/validate-create";
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final Set<String> usernames = new HashSet<>();
    private Map<String, String> sessionIdToUsername = new ConcurrentHashMap<>(); // Using when web connection is closed
    private List<ShapeDTO> shapeList = new LinkedList<>();
    private List<TextDTO> textList  = new LinkedList<>();;
    private List<LineDTO> lineList  = new LinkedList<>();;
    private SimpUserRegistry userRegistry;


    @Autowired
    public UserController(SimpMessagingTemplate simpMessagingTemplate, SimpUserRegistry userRegistry) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRegistry = userRegistry;
    }

    @PostMapping("/setAdmin")
    public ResponseEntity<String> setAdmin(@RequestBody String username) {
        if (adminName == null || adminName.equals(username)) {
            adminName = username;
            return ResponseEntity.ok("Admin username has been set successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has already been set!");
        }
    }

    @GetMapping("/getData")
    public ResponseEntity<String> get(@RequestBody String username) {
        if (adminName == null || adminName.equals(username)) {
            adminName = username;
            return ResponseEntity.ok("Admin username has been set successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has already been set!");
        }
    }

    @MessageMapping("/join")
    public void join(UsernameDTO usernameDTO,  SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = usernameDTO.getName();

        ApiResponse<Map<String, String>> joinResponse = new ApiResponse<>();

        Map responseData = new HashMap();
        responseData.put("username", username);
        responseData.put("sessionId", sessionId);
        joinResponse.setData(responseData);
        sessionIdToUsername.put(sessionId, username);

        if(usernames.contains(username)) {
            // If username occur, should not push forward to admin, directly notiry the
            joinResponse.setSuccess(false);
            simpMessagingTemplate.convertAndSendToUser(sessionId, VALIDATE_USER_PATH, joinResponse);
        } else {
            simpMessagingTemplate.convertAndSend("/topic/admin/join-request", joinResponse);
        }
    }

    @MessageMapping("/join-response")
    public void joinResponse(ApiResponse<String> joinResponse) {
        String sessionId = joinResponse.getData();
        String username = sessionIdToUsername.get(sessionId);
        if (joinResponse.getSuccess()) {
            username = sessionIdToUsername.get(sessionId);
            usernames.add(username);
        }
        joinResponse.setData(username);
        simpMessagingTemplate.convertAndSend(VALIDATE_USER_PATH, joinResponse);
    }

//    @MessageMapping("/addShape")
//    public void addShape(ApiRequest<ShapeDTO> addShapeRequest) {
//        String shapeType = addShapeRequest.getMessage();
//        switch (shapeType) {
//            case "Rectangle":
//
//        }
//        usernames.add(username);
//        simpMessagingTemplate.convertAndSendToUser(sessionId, "/queue/validate-users", joinResponse);
//    }

    @MessageMapping("/addShape")
    @SendTo("/topic/shape")
    public ShapeDTO addShape(ApiRequest<ShapeDTO> apiRequest) {
        ShapeDTO shapeDTO = apiRequest.getData();
        shapeList.add(shapeDTO);
        return shapeDTO;
    }

    @MessageMapping("/addLine")
    @SendTo("/topic/line")
    public LineDTO addLine(ApiRequest<LineDTO> apiRequest) {
        LineDTO lineDTO = apiRequest.getData();
        lineList.add(lineDTO);
        return lineDTO;
    }

    @MessageMapping("/addText")
    @SendTo("/topic/text")
    public TextDTO addText(ApiRequest<TextDTO> apiRequest) {
        TextDTO textDTO = apiRequest.getData();
        textList.add(textDTO);
        return textDTO;
    }

    // Event listener to handle websocket connections
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = sessionIdToUsername.get(sessionId);
        if (username.equals(adminName)) {
            adminName = null; // Admin has left, we need to kick out all the users
            // TODO: Implement kickout function
        } else {
            if (usernames.contains(username)) {
                usernames.remove(username);
            }
            sessionIdToUsername.remove(sessionId);
        }
        System.out.println("WebSocket connection closed with session id: " + sessionId + ", user name: " + username);
    }

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String destination = headers.getDestination();
        System.out.println("WebSocket subscription to " + destination + " with session id: " + sessionId);
    }
 }
