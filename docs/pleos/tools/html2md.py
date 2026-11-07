import sys, os, re
from bs4 import BeautifulSoup
from markdownify import markdownify as md
src, dst, base = sys.argv[1], sys.argv[2], 'https://document.pleos.ai'
for root, _, files in os.walk(src):
    for f in files:
        p = os.path.join(root, f)
        soup = BeautifulSoup(open(p, encoding='utf-8').read(), 'html5lib')
        main = soup.select_one('div.theme-doc-markdown') or soup.select_one('article') or soup.body
        for t in main.select('a.hash-link, button, svg, script, style'): t.decompose()
        for img in main.find_all('img'):
            s = img.get('src','')
            if s.startswith('/'): img['src'] = base + s
        for a in main.find_all('a'):
            h = a.get('href','')
            if h.startswith('/'): a['href'] = base + h
        text = md(str(main), heading_style='ATX', bullets='-')
        text = re.sub(r'\n{3,}', '\n\n', text).strip()
        rel = os.path.relpath(p, src)[:-5] + '.md'
        out = os.path.join(dst, rel); os.makedirs(os.path.dirname(out), exist_ok=True)
        url = base + '/' + os.path.relpath(p, src)[:-5].replace('__index','')
        open(out,'w',encoding='utf-8').write(f'<!-- source: {url} -->\n\n' + text + '\n')
