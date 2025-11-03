# Sample demonstrating nested classes and modules in Ruby

# Style 1: Using scope resolution operator ::
class Car::Engine
  attr_reader :cylinders, :type
  
  def initialize(cylinders, type = "gasoline")
    @cylinders = cylinders
    @type = type
  end
  
  def start
    puts "Starting #{@cylinders}-cylinder #{@type} engine"
  end
end

# Style 2: Using nested blocks
class Motorcycle
  class Engine
    attr_reader :cylinders, :type
    
    def initialize(cylinders, type = "gasoline")
      @cylinders = cylinders
      @type = type
    end
    
    def start
      puts "Starting #{@cylinders}-cylinder #{@type} engine"
    end
  end
  
  # Nested module inside a class
  module SafetyFeatures
    class ABS
      def self.engage
        "ABS engaged"
      end
    end
    
    class TractionControl
      def self.engage
        "Traction control active"
      end
    end
  end
  
  def initialize
    @engine = Engine.new(2)
  end
  
  def start
    puts "Starting motorcycle..."
    @engine.start
  end
end

# Nested modules with both styles
module Vehicle
  # Style 1: Using scope resolution
  module Electric
    def charge
      "Charging electric vehicle"
    end
    
    # Deeply nested class
    class Battery
      attr_reader :capacity
      
      def initialize(capacity)
        @capacity = capacity
      end
      
      def status
        "Battery at #{rand(20..100)}%"
      end
    end
  end
  
  # Style 2: Using nested blocks
  module Combustion
    class Engine
      def initialize(cylinders)
        @cylinders = cylinders
      end
      
      def start
        "Starting #{@cylinders}-cylinder combustion engine"
      end
    end
    
    # Another level of nesting
    module FuelSystem
      class Injector
        def self.clean
          "Cleaning fuel injectors"
        end
      end
    end
  end
  
  # Class using nested modules as mixins
  class Hybrid
    include Electric
    include Combustion
    
    def initialize
      @battery = Electric::Battery.new(75)
      @engine = Combustion::Engine.new(4)
    end
    
    def status
      "Hybrid: #{@battery.status}, #{@engine.start}"
    end
  end
end