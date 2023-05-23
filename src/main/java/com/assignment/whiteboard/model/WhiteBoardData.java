package com.assignment.whiteboard.model;

import com.assignment.whiteboard.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@Getter
public class WhiteBoardData {
    private List<ShapeDTO> shapeList = new LinkedList<>();
    private List<TextDTO> textList = new LinkedList<>();
    private List<LineDTO> lineList = new LinkedList<>();
    private List<ChatMsgDTO> chatMsgList = new LinkedList<>();
    private Set<String> usernames = new HashSet<>();
    private String adminName = null;
    private String FileDirectory = "./files";

    public WhiteBoardData (String filename){
        synchronized(this) {
            // Make directory if there isn't one
            File directory = new File(getFileDirectory());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            loadFromFile(filename);
        }
    }

    // Clear all data in the object
    public void clearAll() {
        synchronized(this) {
            this.shapeList.clear();
            this.lineList.clear();
            this.textList.clear();
        }
    }

    public void addShape(ShapeDTO shape) {
        synchronized(this) {
            shapeList.add(shape);
        }
    }

    public void addText(TextDTO text) {
        synchronized(this) {
            textList.add(text);
        }
    }

    public void addLine(LineDTO line) {
        synchronized(this) {
            lineList.add(line);
        }
    }

    public void addChatMsg(ChatMsgDTO chatMsg) {
        synchronized(this) {
            chatMsgList.add(chatMsg);
        }
    }
     
    public DataDTO getDataDTO ()  {
        DataDTO dataDTO = new DataDTO();
        dataDTO.setShapeList(shapeList);
        dataDTO.setTextList(textList);
        dataDTO.setLineList(lineList);
        dataDTO.setChatMsgList(chatMsgList);
        List<UsernameDTO> usernameDTOS= new LinkedList<>();
        for (String username : usernames) {
            UsernameDTO usernameDTO = new UsernameDTO();
            usernameDTO.setName(username);
            usernameDTOS.add(usernameDTO);
        }
        dataDTO.setUsernameList(usernameDTOS);
        return dataDTO;
    }

    /* Methods to check the existence of a username */
    public boolean contains(String username) {
        return usernames.contains(username);
    }
    // Check existence of a username and delete the username atomatically
    public boolean containsAndDelete(String username) {
        Boolean isContained;
        synchronized(this) {
            isContained = contains(username);
            if (isContained) {
                this.usernames.remove(username);
            }
        }
        return isContained;
    }
    // Note: You should not try to acquire lock of this object in this function, it will incur a dead lock
    // Check whether the username is in the list, and perform a function. This is atomic
    public boolean containsAnd(String username, Consumer<Void> callback) {
        Boolean isContained;
        synchronized(this) {
            isContained = contains(username);
            if (isContained) {
                callback.accept(null);
            }
        }
        return isContained;
    }

    public void setAdminName(String adminName) {
        synchronized (this) {
            this.adminName = adminName;
            addUsername(adminName);
        }
    }

    // Implement set remove method
    public void addUsername(String username) {
        synchronized (this) {
            usernames.add(username);
        }
    }

    /* File methods */
    public void saveToFile(String filename) {
        synchronized(this) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("shapeList", new ArrayList<>(shapeList) );
            dataMap.put("textList", new ArrayList<>(textList));
            dataMap.put("lineList", new ArrayList<>(lineList));
            try {
                // Write to the file.
                mapper.writeValue(new File(getFullPathName(filename)), dataMap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void loadFromFile(String filename) {
        synchronized(this) {
            ObjectMapper mapper = new ObjectMapper();
            DataDTO dataDTO = null;
            try {
                dataDTO = mapper.readValue(new File(getFullPathName(filename)), DataDTO.class);
            } catch (IOException e) {
                System.out.println("Didn't find default file, using emtpy data");
            }
            if (dataDTO != null) {
                this.shapeList.clear();
                this.lineList.clear();
                this.textList.clear();
                this.shapeList.addAll(dataDTO.getShapeList());
                this.lineList.addAll(dataDTO.getLineList());
                this.textList.addAll(dataDTO.getTextList());
            }
        }
    }
    private String getFullPathName (String file) {
       return getFileDirectory() + "/"  + file;
    }
    public List<String> getFileList() {
        File directory = new File(getFileDirectory());
        String[] files = directory.list();
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }

}