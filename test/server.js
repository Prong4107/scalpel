const { urlencoded } = require('express');

const app = require('express')()
app.use(require('body-parser').raw({ type: String }))
jsonifyRequest =  (req) => ({
    url: req.url,
    url_decoded: decodeURIComponent(req.url),
    headers: (
        () => {
            let key; return Object.assign({}, ...req.rawHeaders.map(
                (val, i) => i & 1 ? { [key]: val } : (key = val) && null).filter(e => e)
            )
        }
    )(),
    body: req.body.entries && req.body.toString()
})

app.all('/base64', (req, res) => {
    console.log("Received /base64")
    // Remove date header to ensure identical requests returns the exact same response
    res.setHeader("Date", "[REDACTED]")
    const decoded = new Buffer(req.body.toString('utf-8'), "base64").toString('utf-8');

    res.send(Buffer.from(`Received in base64:\n-----\n${decoded}\n-----`).toString('base64'));
})

app.all('/', (req, res) => {
    console.log("Received")
    // Remove date header to ensure identical requests returns the exact same response
    res.setHeader("Date", "[REDACTED]")
    res.send(jsonifyRequest(req))
})

app.listen(3000)

