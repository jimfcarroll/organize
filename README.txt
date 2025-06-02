from rasa.shared.core.events import UserUttered, BotUttered

def get_conversation_utterances(tracker):
    utterances = []

    for event in tracker.events:
        if isinstance(event, UserUttered):
            utterances.append({
                "speaker": "user",
                "text": event.text,
                "intent": event.intent.get("name") if event.intent else None,
                "entities": event.entities,
                "timestamp": event.timestamp
            })
        elif isinstance(event, BotUttered):
            utterances.append({
                "speaker": "bot",
                "text": event.text,
                "timestamp": event.timestamp
            })

    return utterances
