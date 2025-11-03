# Sample demonstrating ActiveRecord patterns

require 'active_record'

class User < ActiveRecord::Base
  # Associations
  has_many :posts
  has_many :comments
  belongs_to :organization
  has_one :profile
  
  # Validations
  validates :email, presence: true, uniqueness: true
  validates :name, length: { minimum: 2, maximum: 50 }
  validates :age, numericality: { greater_than: 0 }
  
  # Callbacks
  before_save :normalize_email
  after_create :send_welcome_email
  before_destroy :cleanup_data
  
  # Scopes
  scope :active, -> { where(active: true) }
  scope :recent, -> { where('created_at > ?', 1.week.ago) }
  
  def initialize(name, email)
    @name = name
    @email = email
  end
  
  def full_info
    "#{@name} <#{@email}>"
  end
  
  private
  
  def normalize_email
    @email = @email.downcase.strip
  end
  
  def send_welcome_email
    puts "Sending welcome email to #{@email}"
  end
  
  def cleanup_data
    posts.destroy_all
  end
end

class Post < ActiveRecord::Base
  belongs_to :user
  has_many :comments, dependent: :destroy
  
  validates :title, presence: true
  validates :content, length: { minimum: 10 }
  
  before_validation :set_default_title
  
  scope :published, -> { where(published: true) }
  
  private
  
  def set_default_title
    self.title ||= "Untitled"
  end
end

class Organization < ActiveRecord::Base
  has_many :users
  
  validates :name, presence: true, uniqueness: true
end
