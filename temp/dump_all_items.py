import json, sys

def main():
    with open('src/main/resources/com/ultimatetaskmaster/strategy.json', 'r', encoding='utf-8') as f:
        s = json.load(f)
    
    with open('temp/output/all_items_review.txt', 'w', encoding='utf-8') as out:
        for _, v in sorted(s.items(), key=lambda x: x[1].get('taskName', '')):
            name = v.get('taskName', '')
            search = v.get('search', '')
            out.write(f'{name}: {search}\n')
    
    print('Written to temp/output/all_items_review.txt')
    print(f'{len(s)} tasks')

if __name__ == '__main__':
    main()
