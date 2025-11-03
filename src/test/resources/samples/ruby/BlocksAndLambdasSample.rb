# Sample demonstrating blocks, lambdas, and procs

class Calculator
  # Lambda constants
  MULTIPLY = ->(x, y) { x * y }
  DIVIDE = ->(x, y) { y != 0 ? x / y : nil }
  SQUARE = ->(x) { x * x }
  
  # Proc constant
  LOGGER = Proc.new { |msg| puts "[LOG] #{msg}" }
  
  def initialize
    @operations = []
  end
  
  def process_with_block(&block)
    result = yield(10, 5) if block_given?
    @operations << result
    result
  end
  
  def apply_operation(x, y, operation = MULTIPLY)
    operation.call(x, y)
  end
  
  def self.create_adder(increment)
    ->(x) { x + increment }
  end
  
  def map_values(values, &transformer)
    values.map(&transformer)
  end
  
  def demo_splat(values)
    log(*values)
  end

  def demo_keywords
    LOGGER.call(message: "ok", level: :info)
  end

  def demo_hash_literal
    LOGGER.call({ message: "ok", level: :info })
  end

  def demo_mixed_splat_and_keywords(*items, **opts)
    LOGGER.call(*items, **opts)
  end
  
  private
  
  def log(message)
    LOGGER.call(message)
  end
end

class DataProcessor
  FILTER_POSITIVE = ->(x) { x > 0 }
  FILTER_EVEN = ->(x) { x.even? }
  
  def initialize(data)
    @data = data
  end
  
  def filter(&predicate)
    @data.select(&predicate)
  end
  
  def transform(&block)
    @data.map(&block)
  end
  
  def self.default_filter
    FILTER_POSITIVE
  end
end

module Helpers
  FORMATTER = ->(str) { str.upcase.strip }
  VALIDATOR = ->(email) { email.include?('@') }
  
  def self.format(text)
    FORMATTER.call(text)
  end
end
