use std::collections::HashMap;
use std::fmt::Display;

pub struct UserRepository {
    db: Database,
    cache: HashMap<u32, User>,
}

impl UserRepository {
    pub fn new(db: Database) -> Self {
        UserRepository {
            db,
            cache: HashMap::new(),
        }
    }

    pub fn find_by_id(&mut self, id: u32) -> Option<User> {
        if let Some(user) = self.cache.get(&id) {
            return Some(user.clone());
        }

        let user = self.db.query_user(id)?;
        self.cache.insert(id, user.clone());
        Some(user)
    }

    pub fn save(&mut self, user: User) -> Result<(), String> {
        self.db.execute_update(&user)?;
        self.cache.insert(user.id, user);
        Ok(())
    }

    fn clear_cache(&mut self) {
        self.cache.clear();
    }
}

#[derive(Clone, Debug)]
pub struct User {
    pub id: u32,
    pub name: String,
    pub email: String,
}

impl User {
    pub fn new(id: u32, name: String, email: String) -> Self {
        User { id, name, email }
    }

    pub fn display_name(&self) -> String {
        format!("{} ({})", self.name, self.email)
    }
}

pub struct Database {
    connection: String,
}

impl Database {
    pub fn query_user(&self, id: u32) -> Option<User> {
        println!("Querying user with id: {}", id);
        None
    }

    pub fn execute_update(&self, user: &User) -> Result<(), String> {
        println!("Updating user: {:?}", user);
        Ok(())
    }
}

pub fn create_default_user() -> User {
    User::new(0, String::from("Guest"), String::from("guest@example.com"))
}
