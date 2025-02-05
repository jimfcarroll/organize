(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I'm dreaming about Florida" --8bit
{"domain": "travel", "confidence": 0.8}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I'm dreaming about McDonalds" --8bit
{"domain": "dining", "confidence": 0.0}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I'm dreaming about butterflies" --8bit
{"domain": "dining", "confidence": 0.0}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I'm looking for the book 1984" --8bit
{"domain": "public library card catalog", "confidence": 0.0}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I need to bake a cake" --8bit
{"domain": "dining", "confidence": 0.0}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "I need to bake a cake for my trip to Oklahoma" --8bit
Output: {"domain": "dining", "confidence": 0.0}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "Someone stole money from my account!" --8bit
{"domain": "banking", "confidence": 0.9}
(deepseek) jim@master-node:~/src/deepseek$ python ./run_orch.py "Someone stole money from my account after I paid my restaruant bill!" --8bit
{"domain": "banking", "confidence": 0.9}
