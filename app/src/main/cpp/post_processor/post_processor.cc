#include "post_processor/post_processor.h"
#include <sstream>
#include <vector>
#include "utils/string.h"

namespace wenet {
void PostProcessor::InitITNResource(const std::string& tagger_path,
                                    const std::string& verbalizer_path) {
}

std::string PostProcessor::ProcessSpace(const std::string& str) {
  std::string result = str;
  if (opts_.language_type == kMandarinEnglish && !str.empty()) {
    result.clear();
    std::vector<std::string> words;
    std::stringstream ss(str);
    std::string tmp;
    while (ss >> tmp) {
      words.push_back(tmp);
    }
    bool is_englishword_prev = false;
    bool is_englishword_now = false;
    for (std::string& w : words) {
      is_englishword_now = CheckEnglishWord(w);
      if (is_englishword_prev && is_englishword_now) {
        result += (" " + w);
      } else {
        result += (w);
      }
      is_englishword_prev = is_englishword_now;
    }
  }
  result = ProcessBlank(result, opts_.lowercase);
  return result;
}

std::string del_substr(const std::string& str, const std::string& sub) {
  std::string result = str;
  int pos = 0;
  while (std::string::npos != (pos = result.find(sub))) {
    result.erase(pos, sub.size());
  }
  return result;
}

std::string PostProcessor::ProcessSymbols(const std::string& str) {
  std::string result = str;
  result = del_substr(result, "<unk>");
  result = del_substr(result, "<context>");
  result = del_substr(result, "</context>");
  return result;
}

std::string PostProcessor::Process(const std::string& str, bool finish) {
  std::string result;
  result = ProcessSymbols(str);
  result = ProcessSpace(result);
  return result;
}

}  // namespace wenet
