package messages

var langMap = map[string]map[Code]string{
	"zh": zh,
	"en": en,
}

func Message(c Code, lang string) string {
	if m, ok := langMap[lang]; ok {
		if msg, ok := m[c]; ok {
			return msg
		}
	}

	return "Unknown Message"
}
