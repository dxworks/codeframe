class Animal
  attr_accessor :name
  
  def initialize(name)
    @name = name
  end
  
  def speak
    "Some sound"
  end
end

class Dog < Animal
  attr_accessor :breed
  
  def initialize(name, breed)
    super(name)
    @breed = breed
  end
  
  def speak
    "Woof!"
  end
  
  def fetch(item)
    puts "#{name} is fetching #{item}"
  end
end
