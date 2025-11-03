module StringHelpers
  def capitalize_words(text)
    text.split.map(&:capitalize).join(' ')
  end
  
  def reverse_string(text)
    text.reverse
  end
end

class TextProcessor
  include StringHelpers
  
  def process(text)
    result = capitalize_words(text)
    result = reverse_string(result)
    result
  end
end
