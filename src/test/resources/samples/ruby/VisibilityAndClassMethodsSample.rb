# Sample demonstrating visibility modifiers and class methods

class UserService
  @@service_count = 0
  @@settings = {}
  # Class method using self
  def self.find_by_email(email)
    User.where(email: email).first
  end
  
  def self.create_guest
    new("guest@example.com")
  end
  
  # Public instance methods (default)
  def initialize(email)
    @email = email
  end
  
  def process_user
    validate_email
    send_notification
  end
  
  def public_method
    "This is public"
  end
  
  # Private methods
  private
  
  def inline_private
    "inline private"
  end
  
  private def inline_private2
    "inline private 2"
  end
  
  def validate_email
    @email.include?("@")
  end
  
  def send_notification
    puts "Sending notification to #{@email}"
  end
  
  # Protected methods
  protected
  
  def inline_protected
    "inline protected"
  end
  
  protected def inline_protected2
    "inline protected 2"
  end
  
  def internal_helper
    "Protected helper"
  end
  
  # Back to public
  public
  
  def another_public_method
    "Public again"
  end
  
  # Aliasing
  alias old_public_method another_public_method
  
  # Visibility symbol lists
  private :validate_email, :send_notification, :inline_private
  protected :internal_helper, :inline_protected
  public :another_public_method
end

# Class methods using class << self
class Calculator
  @@ops = []
  class << self
    def add(a, b)
      a + b
    end
    
    def multiply(a, b)
      a * b
    end
    
    alias_method :sum, :add
  end
  
  def instance_method
    "Instance method"
  end
end
