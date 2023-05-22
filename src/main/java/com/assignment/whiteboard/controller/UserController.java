package com.assignment.whiteboard.controller;

import com.assignment.whiteboard.dto.*;

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
    private List<ChatMsgDTO> chatList  = new LinkedList<>();;
    private SimpUserRegistry userRegistry;

    @Autowired
    public UserController(SimpMessagingTemplate simpMessagingTemplate, SimpUserRegistry userRegistry) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRegistry = userRegistry;
    }

    @PostMapping("/setAdmin")
    public ResponseEntity<String> setAdmin(@RequestBody String username) {
        if (adminName == null || adminName.equals(username)) {
            usernames.add(username);
            adminName = username;
            return ResponseEntity.ok("Admin username has been set successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has already been set!");
        }
    }

    @GetMapping("/validateUser/{username}")
    public ResponseEntity<ApiResponse<Void>> validateUser(@PathVariable String username) {
        ApiResponse<Void> response = new ApiResponse<>();
        if (usernames.contains(username) || adminName.equals(username)) {
            response.setSuccess(false);
            return ResponseEntity.ok(response);
        } else {
            response.setSuccess(true);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/getData/{username}")
    public ResponseEntity<DataDTO> getData(@PathVariable String username) {
        if (usernames.contains(username) || adminName.equals(username)) {
            List<UsernameDTO> usernameDTOS = new ArrayList<>();
            for (String cur_username : usernames) {
                UsernameDTO cur_usernameDto = new UsernameDTO();
                cur_usernameDto.setName(cur_username);
                usernameDTOS.add(cur_usernameDto);
            }
            DataDTO data = new DataDTO();
            data.setShapeList(shapeList);
            data.setTextList(textList);
            data.setLineList(lineList);
            data.setUsernameList(usernameDTOS);
            data.setChatMsgList(chatList);
            return ResponseEntity.ok(data);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    @PostMapping("/removeUser/{username}")
    public ResponseEntity<String> deleteUser(@PathVariable String username) {
        if (this.adminName != null && (!this.adminName.equals(username))) {
            if (usernames.contains(username)) {
                usernames.remove(username);
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

    // We assume the user which  can subscribe to these topics are all authenticated users
    @MessageMapping("/join")
    public void join(UsernameDTO usernameDTO,  SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = usernameDTO.getName();
        sessionIdToUsername.put(sessionId, username);
        ApiResponse<Map<String, String>> joinResponse = new ApiResponse<>();
        Map <String, String> responseData = new HashMap<>();
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
            usernames.add(username);
            // If user is in the user list, update user list
            UsernameDTO usernameDTO = new UsernameDTO();
            usernameDTO.setName(username);
            simpMessagingTemplate.convertAndSend("/topic/add-user", usernameDTO);
        }
        joinResponse.setData(username);
        simpMessagingTemplate.convertAndSend(VALIDATE_USER_PATH, joinResponse);
    }


    @MessageMapping("/addShape")
    @SendTo("/topic/shape")
    public ShapeDTO addShape(ShapeDTO shapeDTO) {
        shapeList.add(shapeDTO);
        return shapeDTO;
    }

    @MessageMapping("/addLine")
    @SendTo("/topic/line")
    public LineDTO addLine(LineDTO lineDTO) {
        lineList.add(lineDTO);
        return lineDTO;
    }

    @MessageMapping("/addText")
    @SendTo("/topic/text")
    public TextDTO addText(TextDTO textDTO) {
        textList.add(textDTO);
        return textDTO;
    }

    @MessageMapping("/addChat")
    @SendTo("/topic/add-chat")
    public ChatMsgDTO addChat(ChatMsgDTO chatMsgDTO) {
        chatList.add(chatMsgDTO);
        return chatMsgDTO;
    }

    // Event listener to handle websocket connections
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = sessionIdToUsername.get(sessionId);
        if (username == null) {
            System.out.println("Session is not recorded");
            return;
        }

        if (username.equals(adminName)) {
            adminName = null; // Admin has left, we need to kick out all the users
            usernames.remove(username);
            // remove all the users
            // simpMessagingTemplate.convertAndSend("/topic/app-closed", new ApiResponse<>());
            // System.out.println("Admin close the app\n");
        } else {
            if (usernames.contains(username)) {
                usernames.remove(username);
            }
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
