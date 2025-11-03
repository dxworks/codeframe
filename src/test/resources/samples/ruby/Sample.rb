require 'logger'

class UserRepository
  attr_reader :db, :logger
  
  def initialize(db, logger)
    @db = db
    @logger = logger
  end
  
  def find_by_id(id)
    logger.info("Finding user by id: #{id}")
    db.query("SELECT * FROM users WHERE id = ?", id)
  end
  
  def find_all
    db.query_list("SELECT * FROM users")
  end
  
  def save(user)
    if user.id.nil?
      insert(user)
    else
      update(user)
    end
  end
  
  private
  
  def insert(user)
    db.execute("INSERT INTO users (name, email) VALUES (?, ?)", 
               user.name, user.email)
  end
  
  def update(user)
    db.execute("UPDATE users SET name = ?, email = ? WHERE id = ?",
               user.name, user.email, user.id)
  end
end

class User
  attr_accessor :id, :name, :email
  
  def initialize(id = nil, name = nil, email = nil)
    @id = id
    @name = name
    @email = email
  end
end
