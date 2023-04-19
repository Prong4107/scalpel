const { urlencoded } = require('express');

const app = require('express')()
app.use(require('body-parser').raw({ type: String }))

// Known issue: duplicate headers are not supported
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

app.all('/json', (req, res) => {
    console.log("Received")
    // Remove date header to ensure identical requests returns the exact same response
    res.setHeader("Date", "[REDACTED]")
    res.send(jsonifyRequest(req))
})

app.all('/echo', (req, res) => {
    console.log("Received")
    // Remove date header to ensure identical requests returns the exact same response
    res.setHeader("Date", "[REDACTED]")
    res.write("HEADERS:\n");
    rawHeader = req.rawHeaders;
    for (let i = 0; i < rawHeader.length; i += 2) {
        res.write(`${rawHeader[i]}: ${rawHeader[i + 1]}\n`);
    }
    res.write("\nBODY:\n");
    res.write(req.body);
    res.end();
})

app.listen(3000)
