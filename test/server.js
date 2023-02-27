const app = require('express')()
app.use(require('body-parser').raw({ type: String }))

app.all('/', (req, res) => {
    console.log("Received")
    // Remove date header to ensure identical requests returns the exact same response
    res.setHeader("Date", "[REDACTED]")
    res.send({
        url: req.url,
        headers: (
            () => {
                let key; return Object.assign({}, ...req.rawHeaders.map(
                    (val, i) => i & 1 ? { [key]: val } : (key = val) && null).filter(e => e)
                )
            }
        )(),
        body: req.body.entries && req.body.toString()
    })
})

app.listen(3000)

