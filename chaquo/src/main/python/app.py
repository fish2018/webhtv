import requests
import os
import textwrap
from importlib.machinery import SourceFileLoader
import json


def spider(cache, source, file_name=None):
    name = file_name or os.path.basename(source)
    path = cache + '/' + name
    writeFile(path, textwrap.dedent(source).encode())
    name = name.split('.')[0]
    return SourceFileLoader(name, path).load_module().Spider()


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False)
    if 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    else:
        return rsp


def str2json(content):
    return json.loads(content)


def getDependence(ru):
    result = ru.getDependence()
    return result


def getName(ru):
    result = ru.getName()
    return result


def init(ru, extend):
    ru.init(extend)


def homeContent(ru, filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def categoryContent(ru, tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def detailContent(ru, array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def searchContent(ru, key, quick, pg="1"):
    result = ru.searchContent(key, quick, pg)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def playerContent(ru, flag, id, vipFlags):
    result = ru.playerContent(flag, id, str2json(vipFlags))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def liveContent(ru, url):
    result = ru.liveContent(url)
    return result


def localProxy(ru, param):
    result = ru.localProxy(str2json(param))
    return result


def action(ru, action):
    result = ru.action(action)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo



def subtitle_init(ru, config):
    result = ru.init(str2json(config) if isinstance(config, str) and config.strip().startswith(('{', '[')) else config)
    return json.dumps(result if result is not None else {"code": 0, "data": {}}, ensure_ascii=False)


def subtitle_search(ru, request):
    result = ru.search(str2json(request))
    return json.dumps(result, ensure_ascii=False)


def subtitle_resolve(ru, request):
    result = ru.resolve(str2json(request))
    return json.dumps(result, ensure_ascii=False)

def destroy(ru):
    ru.destroy()


def run():
    pass


if __name__ == '__main__':
    run()
