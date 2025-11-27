# Sample demonstrating constants and various require patterns

# Bundler setup and requires
require 'bundler/setup'
Bundler.require(:default)

require 'json'
require 'net/http'
require_relative '../lib/helper'
require_relative 'config'

# File-level constants (captured in fields)
APP_VERSION = "1.0.0"
MAX_CONNECTIONS = 100
DEBUG_MODE = false

# File-level method calls (captured in methodCalls)
puts "Loading ConstantsAndRequiresSample..."
Rails.logger.info("Application starting") if defined?(Rails)

class Configuration
  # Class-level constants
  MAX_RETRIES = 3
  DEFAULT_TIMEOUT = 30
  API_VERSION = "v2"
  ALLOWED_FORMATS = ['json', 'xml', 'csv']
  
  # Nested constant
  Database = {
    host: 'localhost',
    port: 5432
  }
  
  def initialize
    @retries = MAX_RETRIES
    @timeout = DEFAULT_TIMEOUT
  end
  
  def self.api_endpoint
    "https://api.example.com/#{API_VERSION}"
  end
  
  def get_timeout
    DEFAULT_TIMEOUT
  end
  
  def max_retries
    MAX_RETRIES
  end
end

class ApiClient
  BASE_URL = "https://api.example.com"
  MAX_CONNECTIONS = 10
  RETRY_DELAY = 5
  
  attr_reader :timeout
  
  def initialize(timeout = Configuration::DEFAULT_TIMEOUT)
    @timeout = timeout
    @max_retries = Configuration::MAX_RETRIES
  end
  
  def self.default_headers
    {
      'Content-Type' => 'application/json',
      'API-Version' => Configuration::API_VERSION
    }
  end
  
  def fetch_data(endpoint)
    url = "#{BASE_URL}/#{endpoint}"
    # Implementation
  end
  
  private
  
  def retry_request
    sleep(RETRY_DELAY)
  end
end

module HttpHelpers
  TIMEOUT = 60
  MAX_REDIRECTS = 5
  
  def self.get(url)
    # Implementation
  end
end
