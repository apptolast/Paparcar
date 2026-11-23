const METERS_PER_DEGREE = 111195;

function isPosition(position) {
    return Array.isArray(position) && Number.isFinite(position[0]) &&
        Number.isFinite(position[1]) && Math.abs(position[0]) <= 180 &&
        Math.abs(position[1]) <= 90;
}

function isRing(ring) {
    return Array.isArray(ring) && ring.length >= 4 && ring.every(isPosition) &&
        ring[0][0] === ring.at(-1)[0] && ring[0][1] === ring.at(-1)[1];
}

function measureRing(ring, [longitude, latitude]) {
    const scaleX = METERS_PER_DEGREE * Math.cos(latitude * Math.PI / 180);
    const points = ring.map(([lon, lat]) => [
        (((lon - longitude + 540) % 360) - 180) * scaleX,
        (lat - latitude) * METERS_PER_DEGREE,
    ]);
    let inside = false;
    let edgeDistance = Infinity;
    for (let i = 1; i < points.length; i += 1) {
        const [ax, ay] = points[i - 1];
        const [bx, by] = points[i];
        const dx = bx - ax;
        const dy = by - ay;
        const lengthSquared = dx * dx + dy * dy;
        const t = lengthSquared === 0 ? 0 :
            Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        edgeDistance = Math.min(edgeDistance, Math.hypot(ax + t * dx, ay + t * dy));
        if ((ay > 0) !== (by > 0) && ax - ay * dx / dy > 0) inside = !inside;
    }
    return { inside, edgeDistance };
}

// Local approximation for station-sized footprints. Accuracy describes the reported fix,
// and the result describes the mapped footprint rather than the purpose of the visit.
export function osmFootprintMatch(position, accuracyMeters, footprint) {
    if (footprint == null || footprint.type === 'Point') return 'unknown';

    const polygons = footprint.type === 'Polygon' ? [footprint.coordinates] :
        footprint.type === 'MultiPolygon' ? footprint.coordinates : null;
    if (!isPosition(position) || !Array.isArray(polygons) || polygons.length === 0 ||
        !polygons.every(polygon => Array.isArray(polygon) && polygon.length > 0 && polygon.every(isRing))) {
        throw new Error('Invalid OSM footprint or fix position');
    }
    if (accuracyMeters === null) return 'unknown';

    let nearBoundary = false;
    for (const polygon of polygons) {
        const [outer, ...holes] = polygon.map(ring => measureRing(ring, position));
        const inside = outer.inside && holes.every(hole => !hole.inside);
        const edgeDistance = Math.min(outer.edgeDistance, ...holes.map(hole => hole.edgeDistance));
        if (inside && edgeDistance > accuracyMeters) return 'inside';
        if (edgeDistance <= accuracyMeters) nearBoundary = true;
    }
    return nearBoundary ? 'boundary' : 'outside';
}
