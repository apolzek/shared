const http = require('http');

let items = [];

const server = http.createServer((req, res) => {
    const { method, url } = req;
    
    // Definir headers CORS logo no início
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');
    res.setHeader('Access-Control-Max-Age', '86400');
    res.setHeader('Content-Type', 'application/json');

    // Tratar requisições OPTIONS (preflight)
    if (method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    let body = [];

    req.on('data', chunk => {
        body.push(chunk);
    }).on('end', () => {
        body = Buffer.concat(body).toString();

        if (url === '/api/items' && method === 'GET') {
            res.writeHead(200);
            res.end(JSON.stringify(items));
        } else if (url === '/api/items' && method === 'POST') {
            try {
                const item = JSON.parse(body);
                items.push(item);
                res.writeHead(201);
                res.end(JSON.stringify({ message: 'Item added', item }));
            } catch (error) {
                res.writeHead(400);
                res.end(JSON.stringify({ message: 'Invalid JSON' }));
            }
        } else if (url.startsWith('/api/items/') && method === 'PUT') {
            const id = parseInt(url.split('/')[3]);
            if (id >= 0 && id < items.length) {
                try {
                    const updatedItem = JSON.parse(body);
                    items[id] = updatedItem;
                    res.writeHead(200);
                    res.end(JSON.stringify({ message: 'Item updated', updatedItem }));
                } catch (error) {
                    res.writeHead(400);
                    res.end(JSON.stringify({ message: 'Invalid JSON' }));
                }
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ message: 'Item not found' }));
            }
        } else if (url.startsWith('/api/items/') && method === 'DELETE') {
            const id = parseInt(url.split('/')[3]);
            if (id >= 0 && id < items.length) {
                items.splice(id, 1);
                res.writeHead(200);
                res.end(JSON.stringify({ message: 'Item deleted' }));
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ message: 'Item not found' }));
            }
        } else {
            res.writeHead(404);
            res.end(JSON.stringify({ message: 'Route not found' }));
        }
    });
});

const PORT = 3002;
server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});