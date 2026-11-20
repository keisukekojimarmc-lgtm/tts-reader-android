package com.example.minutesreader.render

import com.example.minutesreader.data.MinuteParser
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentRenderer

data class TocItem(val level: Int, val text: String)

data class RenderedDoc(val html: String, val toc: List<TocItem>, val speech: String)

/**
 * 議事録 Markdown をスマホで読みやすい HTML に変換する。
 * - フロントマターは「日時・区分・参加者」のカードにまとめ、残りは「詳細」に折りたたむ
 * - 3 列以上の表（ToDo など）は狭い画面ではカード表示に切り替える
 * - （要確認）を蛍光ペン風に、[00:00〜06:00] のタイムスタンプを小さく表示
 */
object MinutesRenderer {

    private val EXT = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(EXT).build()
    private val html: HtmlRenderer = HtmlRenderer.builder().extensions(EXT).escapeHtml(true).sanitizeUrls(true).build()
    private val text: TextContentRenderer = TextContentRenderer.builder().extensions(EXT).build()

    /** ヘッダーカードの本文側に出す項目と、その表示名。 */
    private val DETAIL_LABELS = linkedMapOf(
        "type" to "種別", "source_audio" to "録音", "transcript" to "文字起こし",
        "notion" to "Notion", "memo" to "メモ", "created" to "作成日",
    )
    private val MAIN_KEYS = setOf("date", "time", "category", "title", "participants")

    fun render(raw: String, fallbackTitle: String, dark: Boolean): RenderedDoc {
        val fm = MinuteParser.splitFrontMatter(raw)
        val doc = parser.parse(fm.body)

        val toc = ArrayList<TocItem>()
        var hasH1 = false
        doc.accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                if (heading.level == 1) hasH1 = true
                if (heading.level == 2 || heading.level == 3) toc += TocItem(heading.level, plain(heading).trim())
                visitChildren(heading)
            }
        })

        val f = fm.fields
        val title = f["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val header = buildString {
            append("<header class=\"meta\">")
            f["category"]?.takeIf { it.isNotBlank() }?.let { append("<span class=\"cat\">").append(esc(it)).append("</span>") }
            if (!hasH1) append("<div class=\"title\">").append(esc(title)).append("</div>")
            val whenText = listOfNotNull(
                f["date"]?.takeIf { it.isNotBlank() }?.let(MinuteParser::longDate),
                f["time"]?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            if (whenText.isNotEmpty()) append("<div class=\"when\">🗓 ").append(esc(whenText)).append("</div>")
            f["participants"]?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"people\">👥 ").append(esc(it)).append("</div>")
            }
            val details = f.filterKeys { it !in MAIN_KEYS }.filterValues { it.isNotBlank() }
            if (details.isNotEmpty()) {
                append("<details><summary>詳細</summary><dl>")
                details.forEach { (k, v) ->
                    append("<dt>").append(esc(DETAIL_LABELS[k] ?: k)).append("</dt><dd>").append(linkify(v)).append("</dd>")
                }
                append("</dl></details>")
            }
            append("</header>")
        }
        val hasHeader = f.isNotEmpty()

        val speech = buildString {
            append(title).append("。\n")
            append(text.render(doc))
        }

        val page = """<!doctype html><html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>$CSS</style></head>
<body class="${if (dark) "dark" else ""}">${if (hasHeader) header else ""}<article>${html.render(doc)}</article>
<script>$JS</script></body></html>"""
        return RenderedDoc(page, toc, speech)
    }

    private fun plain(node: Node): String {
        val sb = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(t: Text) { sb.append(t.literal) }
            override fun visit(c: Code) { sb.append(c.literal) }
        })
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val URL_RE = Regex("""https?://[^\s（）()<>"]+""")

    private fun linkify(v: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in URL_RE.findAll(v)) {
            sb.append(esc(v.substring(last, m.range.first)))
            sb.append("<a href=\"").append(esc(m.value)).append("\">").append(esc(m.value)).append("</a>")
            last = m.range.last + 1
        }
        sb.append(esc(v.substring(last)))
        return sb.toString()
    }

    private val CSS = """
:root{--bg:#fbfaf7;--fg:#1f2328;--muted:#5f6670;--line:#e4e1da;--accent:#2f6f9f;--card:#fff;--soft:#f3f1ec;--mark:#fff0b3;--markfg:#5c4500;--code:#efede7}
body.dark{--bg:#15171a;--fg:#e7e7e4;--muted:#a0a6ad;--line:#30343a;--accent:#86b9e3;--card:#1e2125;--soft:#23272c;--mark:#4d3f0f;--markfg:#ffe08a;--code:#262a30}
html{-webkit-text-size-adjust:100%}
body{margin:0;padding:14px 16px 120px;background:var(--bg);color:var(--fg);font-family:"Noto Sans JP","Noto Sans CJK JP",system-ui,sans-serif;font-size:17px;line-height:1.85;overflow-wrap:anywhere;-webkit-tap-highlight-color:transparent}
.meta{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 14px;margin:2px 0 18px;font-size:15px;line-height:1.6}
.meta .cat{display:inline-block;font-size:12px;font-weight:700;color:var(--accent);border:1px solid var(--accent);border-radius:999px;padding:0 10px;margin-bottom:6px}
.meta .title{font-size:1.2em;font-weight:700;margin:2px 0 6px}
.meta .when,.meta .people{margin-top:3px}
.meta .when{color:var(--muted)}
details{margin-top:8px}
summary{color:var(--muted);font-size:14px}
dl{font-size:13px;color:var(--muted);margin:6px 0 0}dt{font-weight:700;margin-top:6px}dd{margin:0}
h1{font-size:1.3em;line-height:1.5;margin:4px 0 14px}
h2{font-size:1.15em;line-height:1.5;margin:34px 0 10px;padding-bottom:6px;border-bottom:2px solid var(--line)}
h3{font-size:1.03em;line-height:1.55;margin:26px 0 8px;padding-left:10px;border-left:4px solid var(--accent)}
h4{font-size:1em;margin:20px 0 6px}
p{margin:10px 0}
ul,ol{padding-left:1.35em;margin:8px 0}li{margin:6px 0}li>ul,li>ol{margin:4px 0}
blockquote{margin:12px 0;padding:8px 12px;background:var(--soft);border-radius:10px;color:var(--muted);font-size:.9em;line-height:1.7}
blockquote p{margin:4px 0}
.tbl{overflow-x:auto;margin:12px 0;border:1px solid var(--line);border-radius:12px;background:var(--card)}
table{border-collapse:collapse;width:100%;font-size:.93em;line-height:1.6}
th,td{border-bottom:1px solid var(--line);padding:8px 10px;text-align:left;vertical-align:top}
th{background:var(--soft);white-space:nowrap;font-size:.92em}
tr:last-child td{border-bottom:none}
@media (max-width:700px){
.tbl.cards{border:none;background:none;overflow:visible}
.cards table,.cards tbody,.cards tr,.cards td{display:block;width:auto}
.cards thead{display:none}
.cards tr{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:8px 12px;margin:10px 0}
.cards td{border:none;padding:2px 0}
.cards td::before{content:attr(data-label);display:block;font-size:12px;color:var(--muted);font-weight:700}
.cards td[data-label=""]::before{display:none}
}
code{background:var(--code);padding:1px 5px;border-radius:5px;font-size:.88em}
pre{background:var(--code);padding:10px 12px;border-radius:10px;overflow-x:auto;line-height:1.5}
pre code{padding:0;background:none}
a{color:var(--accent);word-break:break-all}
hr{border:none;border-top:1px solid var(--line);margin:26px 0}
mark.chk{background:var(--mark);color:var(--markfg);border-radius:4px;padding:0 3px}
.ts{font-size:.72em;color:var(--muted);font-weight:400;white-space:nowrap}
li input[type=checkbox]{transform:scale(1.15);margin:0 6px 0 0}
"""

    private val JS = """
(function(){
var art=document.querySelector('article');if(!art)return;
var h1=art.firstElementChild,hd=document.querySelector('header.meta');if(h1&&h1.tagName==='H1'&&hd)hd.parentNode.insertBefore(h1,hd);
var hs=art.querySelectorAll('h2,h3');for(var i=0;i<hs.length;i++)hs[i].id='h'+i;
var ts=art.querySelectorAll('table');for(var k=0;k<ts.length;k++){var t=ts[k];
var heads=[].map.call(t.querySelectorAll('thead th'),function(th){return th.textContent.trim()});
var w=document.createElement('div');w.className='tbl'+(heads.length>=3?' cards':'');
t.parentNode.insertBefore(w,t);w.appendChild(t);
[].forEach.call(t.querySelectorAll('tbody tr'),function(r){[].forEach.call(r.children,function(td,j){td.setAttribute('data-label',heads[j]||'')})});}
var re=/([（(]要確認[^）)]*[）)]|要確認)|(\[\d{1,2}:\d{2}[^\]\n]*\])/g;
var wk=document.createTreeWalker(art,NodeFilter.SHOW_TEXT,null),nodes=[];while(wk.nextNode())nodes.push(wk.currentNode);
nodes.forEach(function(n){var s=n.nodeValue;re.lastIndex=0;if(!re.test(s))return;
var p=n.parentNode;if(p.closest&&p.closest('code,pre,a'))return;
var inH=p.closest&&p.closest('h1,h2,h3,h4');
var f=document.createDocumentFragment(),last=0,m;re.lastIndex=0;
while((m=re.exec(s))){if(m[1]&&inH)continue;
if(m.index>last)f.appendChild(document.createTextNode(s.slice(last,m.index)));
var el=document.createElement(m[1]?'mark':'span');el.className=m[1]?'chk':'ts';el.textContent=m[0];f.appendChild(el);last=re.lastIndex;}
if(last<s.length)f.appendChild(document.createTextNode(s.slice(last)));p.replaceChild(f,n);});
})();
"""
}
