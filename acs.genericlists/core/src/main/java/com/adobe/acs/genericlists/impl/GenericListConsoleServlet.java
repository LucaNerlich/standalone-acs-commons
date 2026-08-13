package com.adobe.acs.genericlists.impl;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import java.io.IOException;

/**
 * Small, dependency-free author console for standalone Generic Lists.
 *
 * <p>The console delegates all repository work to {@link GenericListManagementServlet}; it deliberately uses the
 * current authenticated user's session and retrieves AEM's CSRF token before mutating content.</p>
 */
@Component(service = Servlet.class, property = "sling.servlet.methods=" + HttpConstants.METHOD_GET)
@SlingServletPaths("/bin/acs-genericlists/console")
@ServiceDescription("Generic Lists Authoring Console")
public final class GenericListConsoleServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(PAGE);
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Generic Lists</title>
              <style>
                :root { color-scheme: light; font-family: Adobe Clean, Arial, sans-serif; color: #242424; }
                body { margin: 2rem auto; max-width: 1200px; padding: 0 1rem; background: #f5f5f5; }
                h1 { margin-bottom: .25rem; } .muted { color: #6e6e6e; }
                section { background: white; padding: 1rem; margin: 1rem 0; border: 1px solid #ddd; border-radius: .25rem; }
                form, .toolbar { display: flex; gap: .6rem; align-items: end; flex-wrap: wrap; }
                label { display: grid; gap: .25rem; font-size: .85rem; font-weight: 600; }
                input, select, textarea, button { font: inherit; padding: .45rem .55rem; }
                input, textarea, select { border: 1px solid #8e8e8e; border-radius: .2rem; min-width: 12rem; }
                textarea { width: 100%; min-height: 13rem; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
                button { color: white; background: #1473e6; border: 0; border-radius: .2rem; cursor: pointer; }
                button.secondary { color: #242424; background: #eaeaea; } button.danger { background: #d7373f; }
                table { width: 100%; border-collapse: collapse; } th, td { padding: .65rem; border-bottom: 1px solid #ddd; text-align: left; vertical-align: top; }
                td.actions { display: flex; gap: .4rem; flex-wrap: wrap; } code { word-break: break-all; }
                #message { min-height: 1.4rem; padding: .45rem; } #message.error { color: #b40000; } #message.ok { color: #147300; }
                .invalid { color: #b40000; font-weight: 600; } dialog { width: min(800px, 94vw); border: 1px solid #777; border-radius: .3rem; }
              </style>
            </head>
            <body>
              <h1>Generic Lists</h1>
              <p class="muted">Manage standalone lists, validate/import/export values, see where lists are used, and publish when replication is available.</p>
              <div id="message" role="status"></div>
              <section>
                <div class="toolbar">
                  <label>Root <input id="root" value="/content/generic-lists" aria-label="List root"></label>
                  <label>Search <input id="search" placeholder="Name or path"></label>
                  <button id="refresh" type="button">Refresh</button>
                  <span class="muted">Migration API: POST /bin/acs-genericlists/migrate (dry-run first)</span>
                </div>
              </section>
              <section>
                <h2>Create list</h2>
                <form id="create-form">
                  <label>Path <input name="path" required placeholder="/content/generic-lists/countries"></label>
                  <label>Display title <input name="title" placeholder="Countries"></label>
                  <label>Description <input name="description" placeholder="Optional authoring note"></label>
                  <button>Create</button>
                </form>
              </section>
              <section>
                <h2>Lists</h2>
                <table><thead><tr><th>List</th><th>Items</th><th>State</th><th>Actions</th></tr></thead><tbody id="lists"></tbody></table>
              </section>
              <dialog id="import-dialog">
                <form method="dialog"><button class="secondary" value="cancel">Close</button></form>
                <h2 id="import-title">Import</h2>
                <p class="muted">JSON follows the export shape. CSV headers are <code>title,value,locale,localizedTitle</code>. Import replaces the list rows after validation.</p>
                <label>Format <select id="import-format"><option value="json">JSON</option><option value="csv">CSV</option></select></label>
                <label>File <input id="import-file" type="file" accept=".json,.csv,application/json,text/csv"></label>
                <label>Payload <textarea id="import-payload" spellcheck="false"></textarea></label>
                <button id="import-submit" type="button">Validate and import</button>
              </dialog>
              <script>
              (() => {
                const api = '/bin/acs-genericlists/lists';
                const $ = id => document.getElementById(id);
                let csrfToken, currentImportPath;
                const message = (text, error=false) => { const el=$('message'); el.textContent=text; el.className=error?'error':'ok'; };
                async function token() { if (csrfToken) return csrfToken; const r=await fetch('/libs/granite/csrf/token.json',{credentials:'same-origin'}); csrfToken=(await r.json()).token; return csrfToken; }
                async function call(method, params={}, body) {
                  const qs=new URLSearchParams(params); const options={method,credentials:'same-origin',headers:{}};
                  if (method !== 'GET') { options.headers['CSRF-Token']=await token(); if (body !== undefined) options.body=body; }
                  const response=await fetch(api+'?'+qs,options);
                  const text=await response.text(); let data; try { data=JSON.parse(text); } catch (_) { data={error:text}; }
                  if (!response.ok) throw new Error(data.error || response.statusText); return data;
                }
                const action = async (name, path) => {
                  try {
                    if (name==='delete' && !confirm('Delete '+path+'?')) return;
                    if (name==='copy' || name==='move') { const destination=prompt('Destination path', path+'-copy'); if (!destination) return; await call('POST',{action:name,source:path,destination}); }
                    else if (name==='publish' || name==='unpublish') await call('POST',{action:name,path});
                    else if (name==='usage') { const d=await call('GET',{action:'usage',path}); alert(d.usages.length ? d.usages.join('\\n') : 'No readable usage found.'); return; }
                    else if (name==='import') { currentImportPath=path; $('import-title').textContent='Import '+path; $('import-payload').value=''; $('import-dialog').showModal(); return; }
                    else if (name==='json' || name==='csv') { window.open(api+'?'+new URLSearchParams({action:'export',path,format:name}),'_blank'); return; }
                    message('Completed '+name+' for '+path); await refresh();
                  } catch (e) { message(e.message,true); }
                };
                function button(label, name, path, danger=false) { const b=document.createElement('button'); b.type='button'; b.textContent=label; b.className=danger?'danger':'secondary'; b.onclick=()=>action(name,path); return b; }
                function render(data) { const body=$('lists'); body.replaceChildren(); data.lists.forEach(list => {
                  const row=document.createElement('tr'); const info=document.createElement('td'); const title=document.createElement('strong'); title.textContent=list.title; info.append(title,document.createElement('br')); const path=document.createElement('code'); path.textContent=list.path; info.append(path); if(list.description){const d=document.createElement('div');d.className='muted';d.textContent=list.description;info.append(d);}
                  const count=document.createElement('td');count.textContent=list.items; const state=document.createElement('td');state.textContent=list.published?'Published':'Not published'; if(!list.valid){const i=document.createElement('div');i.className='invalid';i.textContent=list.validationIssues+' validation issue(s)';state.append(document.createElement('br'),i);} const actions=document.createElement('td');actions.className='actions'; [['JSON','json'],['CSV','csv'],['Import','import'],['Usage','usage'],['Copy','copy'],['Move','move'],[list.published?'Unpublish':'Publish',list.published?'unpublish':'publish'],['Delete','delete',true]].forEach(([label,name,danger])=>actions.append(button(label,name,list.path,danger))); row.append(info,count,state,actions);body.append(row); });
                }
                async function refresh() { try { const d=await call('GET',{action:'list',root:$('root').value,q:$('search').value}); render(d); message(d.count+' list(s) loaded.'); } catch(e) { message(e.message,true); } }
                $('refresh').onclick=refresh; $('search').addEventListener('keydown',e=>{if(e.key==='Enter')refresh();});
                $('create-form').onsubmit=async e=>{e.preventDefault();try{const f=new FormData(e.currentTarget);await call('POST',{action:'create',path:f.get('path'),title:f.get('title'),description:f.get('description')});message('List created.');e.currentTarget.reset();await refresh();}catch(err){message(err.message,true);}};
                $('import-file').onchange=async e=>{const file=e.target.files[0];if(file){$('import-payload').value=await file.text();$('import-format').value=file.name.endsWith('.csv')?'csv':'json';}};
                $('import-submit').onclick=async()=>{try{await call('POST',{action:'import',path:currentImportPath,format:$('import-format').value},$('import-payload').value);$('import-dialog').close();message('Import completed.');await refresh();}catch(e){message(e.message,true);}};
                refresh();
              })();
              </script>
            </body></html>
            """;
}
