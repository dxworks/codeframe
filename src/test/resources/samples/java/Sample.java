package com.example.demo;

import java.util.List;
import java.util.stream.Collectors;

@Resource(name = RestConstants.VERSION_1 + "/user", supportedClass = Sample.class, supportedVersions = {
    "1.0.* - 2.0.*" })
public class Sample {
    private final DatabaseConnection db;
    private final Logger logger;
    private List<User> users;
    
    public Sample(DatabaseConnection db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }
    
    public User findById(String id) {
        logger.info("Finding user by id: " + id);
        db.query().debug().printoTo(output());
        return db.query("SELECT * FROM users WHERE id = ?", id);
    }
    
    public List<User> findAll() {
        return db.queryList("SELECT * FROM users");
    }
    
    public void logUser(final String id) {
        logger.info(id);
    }
    
    public void setUsers(final List<User> users) {
        this.users = users;
    }
    
    public void save(User user) {
        if (user.getId() == null) {
            insert(user);
        } else {
            update(user);
        }
    }
    
    private void insert(User user) {
        db.execute("INSERT INTO users (name, email) VALUES (?, ?)", 
                   user.getName(), user.getEmail());
    }
    
    private void update(User user) {
        db.execute("UPDATE users SET name = ?, email = ? WHERE id = ?",
                   user.getName(), user.getEmail(), user.getId());
    }
}

class User {
    private String id;
    private String name;
    private String email;
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
