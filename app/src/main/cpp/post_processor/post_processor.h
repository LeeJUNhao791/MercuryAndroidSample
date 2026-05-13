#ifndef POST_PROCESSOR_POST_PROCESSOR_H_
#define POST_PROCESSOR_POST_PROCESSOR_H_

#include <memory>
#include <string>
#include <utility>
#include "utils/utils.h"

namespace wenet {

enum LanguageType {
  kMandarinEnglish = 0x00,
  kIndoEuropean = 0x01
};

struct PostProcessOptions {
  LanguageType language_type = kMandarinEnglish;
  bool lowercase = true;
  bool itn = false;
};

class PostProcessor {
 public:
  explicit PostProcessor(PostProcessOptions&& opts) : opts_(std::move(opts)) {}
  explicit PostProcessor(const PostProcessOptions& opts) : opts_(opts) {}
  std::string Process(const std::string& str, bool finish);
  std::string ProcessSpace(const std::string& str);
  std::string ProcessSymbols(const std::string& str);

  void InitITNResource(const std::string& tagger_path,
                       const std::string& verbalizer_path);

 private:
  const PostProcessOptions opts_;

 public:
  WENET_DISALLOW_COPY_AND_ASSIGN(PostProcessor);
};

}  // namespace wenet

#endif  // POST_PROCESSOR_POST_PROCESSOR_H_
