package com.assignment.whiteboard.controller;

import com.assignment.whiteboard.constants.URI;
import com.assignment.whiteboard.dto.*;

import com.assignment.whiteboard.model.WhiteBoardData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class UserController {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private Map<String, String> sessionIdToUsername = new ConcurrentHashMap<>(); // Using when web connection is closed
    private String default_file = "default.json";
    private WhiteBoardData whiteBoardData = new WhiteBoardData(default_file);

    @Autowired
    public UserController(SimpMessagingTemplate simpMessagingTemplate, SimpUserRegistry userRegistry) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @PostMapping("/setAdmin")
    public ResponseEntity<String> setAdmin(@RequestBody String username) {
        if (whiteBoardData.getAdminName() == null) {
            whiteBoardData.setAdminName(username);
            return ResponseEntity.ok("Admin username has been set successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has already been set!");
        }
    }

    @GetMapping("/validateUser/{username}")
    public ResponseEntity<ApiResponse<Void>> validateUser(@PathVariable String username) {
        ApiResponse<Void> response = new ApiResponse<>();
        Boolean isContained = whiteBoardData.containsAnd(username, unused -> {});
        if (isContained) {
            response.setSuccess(false);
            response.setError("username is already exists");
        } else if ( whiteBoardData.getAdminName() == null) {
            response.setSuccess(false);
            response.setError("admin has not create a whiteboard");
        } else {
            response.setSuccess(true);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/getData/{username}")
    public ResponseEntity<DataDTO> getData(@PathVariable String username) {
        String adminName = whiteBoardData.getAdminName();
        if (whiteBoardData.contains(username) || adminName.equals(username)) {
            return ResponseEntity.ok(whiteBoardData.getDataDTO());
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    @PostMapping("/removeUser/{username}")
    public ResponseEntity<String> deleteUser(@PathVariable String username) {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null && (!adminName.equals(username))) {
            ;
            if (whiteBoardData.containsAndDelete(username)) {
                UsernameDTO usernameDTO = new UsernameDTO();
                usernameDTO.setName(username);
                simpMessagingTemplate.convertAndSend("/topic/remove-user", usernameDTO);
                return ResponseEntity.ok("User " + username + " has been deleted successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("User " + username + " does not exist.");
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot kick out yourself");
        }
    }

    // Create a new whiteboard, clear all current shape, line, text data, using rest
    @PostMapping("/createWhiteboard")
    public ResponseEntity<String> createWhiteboard() {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null) {
            whiteBoardData.clearAll();
            simpMessagingTemplate.convertAndSend(URI.UPDATE_DATA, whiteBoardData.getDataDTO());
            return ResponseEntity.ok("Whiteboard has been created successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has not been set yet.");
        }
    }

    // Upload data of whiteboard, including text, line and shape, save to a file
    @PostMapping("/saveData")
    public ResponseEntity<String> uploadData(@RequestParam(value = "filename", required = false) String filename) {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null) {
            if (filename != null) {
                whiteBoardData.saveToFile(filename);
            } else {
                whiteBoardData.saveToFile(default_file);
            }
            return ResponseEntity.ok("Data has been saved successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has not been set yet.");
        }
    }

    // Load data from a local file, post request
    @PostMapping("/loadData/{filename}")
    public ResponseEntity<String> loadData(@PathVariable String filename) {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null) {
            whiteBoardData.loadFromFile(filename);
            simpMessagingTemplate.convertAndSend(URI.UPDATE_DATA, whiteBoardData.getDataDTO());
            return ResponseEntity.ok("Data has been loaded successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has not been set yet.");
        }
    }

    // Get the file list in the server's file directory, server automatically checkout the /files directory
    @GetMapping("/getFileList")
    public ResponseEntity<List<String>> getFileList() {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null) {
            return ResponseEntity.ok(whiteBoardData.getFileList());
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // Close the application, send an message to a /topic/close to all users
    @PostMapping("/closeApp")
    public ResponseEntity<String> close() {
        String adminName = whiteBoardData.getAdminName();
        if (adminName != null) {

            ApiResponse<String> apiResponse = new ApiResponse<>();
            apiResponse.setSuccess(true);
            simpMessagingTemplate.convertAndSend(URI.CLOSE_APP_PATH, apiResponse);
            whiteBoardData.setAdminName(null);
            return ResponseEntity.ok("Close request has been sent successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has not been set yet.");
        }
    }

    // We assume the user which  can subscribe to these topics are all authenticated users
    @MessageMapping("/adminJoin")
    public void AdminJoin(UsernameDTO usernameDTO, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = usernameDTO.getName();
        sessionIdToUsername.put(sessionId, username);
        ApiResponse<String> response = new ApiResponse<>();
        response.setData(username);
        response.setSuccess(true);
        simpMessagingTemplate.convertAndSend("/topic/admin/init", response);
    }

    // We assume the user which  can subscribe to these topics are all authenticated users
    @MessageMapping("/join")
    public void join(UsernameDTO usernameDTO, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = usernameDTO.getName();
        sessionIdToUsername.put(sessionId, username);
        ApiResponse<Map<String, String>> joinResponse = new ApiResponse<>();
        Map<String, String> responseData = new HashMap<>();
        responseData.put("username", username);
        responseData.put("sessionId", sessionId);
        joinResponse.setData(responseData);
        simpMessagingTemplate.convertAndSend("/topic/admin/join-request", joinResponse);
    }

    @MessageMapping("/join-response")
    public void joinResponse(ApiResponse<String> joinResponse) {
        String sessionId = joinResponse.getData();
        String username = sessionIdToUsername.get(sessionId);
        if (joinResponse.getSuccess()) {
            username = sessionIdToUsername.get(sessionId);
            whiteBoardData.addUsername(username);
            // If user is in the user list, update user list
            UsernameDTO usernameDTO = new UsernameDTO();
            usernameDTO.setName(username);
            simpMessagingTemplate.convertAndSend("/topic/add-user", usernameDTO);
        }
        joinResponse.setData(username);
        simpMessagingTemplate.convertAndSend(URI.VALIDATE_USER_PATH, joinResponse);
    }


    @MessageMapping("/addShape")
    @SendTo("/topic/shape")
    public ShapeDTO addShape(ShapeDTO shapeDTO) {
        whiteBoardData.addShape(shapeDTO);
        return shapeDTO;
    }

    @MessageMapping("/addLine")
    @SendTo("/topic/line")
    public LineDTO addLine(LineDTO lineDTO) {
        whiteBoardData.addLine(lineDTO);
        return lineDTO;
    }

    @MessageMapping("/addText")
    @SendTo("/topic/text")
    public TextDTO addText(TextDTO textDTO) {
        whiteBoardData.addText(textDTO);
        return textDTO;
    }

    @MessageMapping("/addChat")
    @SendTo("/topic/add-chat")
    public ChatMsgDTO addChat(ChatMsgDTO chatMsgDTO) {
        whiteBoardData.addChatMsg(chatMsgDTO);
        return chatMsgDTO;
    }

    // Event listener to handle websocket connections
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = sessionIdToUsername.get(sessionId);
        String adminName = whiteBoardData.getAdminName();
        if (username == null) {
            System.out.println("Session is not recorded");
            return;
        }

        if (username.equals(adminName)) {
            simpMessagingTemplate.convertAndSend(URI.CLOSE_APP_PATH, "close");
            whiteBoardData.setAdminName(null);
            whiteBoardData.containsAndDelete(username);
        } else {
            whiteBoardData.containsAndDelete(username);
            // If user is in the user list, remove user from the user list
            UsernameDTO usernameDTO = new UsernameDTO();
            usernameDTO.setName(username);
            simpMessagingTemplate.convertAndSend("/topic/remove-user", usernameDTO);
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
