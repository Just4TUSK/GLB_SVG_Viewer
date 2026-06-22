Задача включает в себя разработку Android приложения, которое позволяет просматривать 3D/2D модели автомобилей внутри браузерной страницы.
Для разработки использовано Kotlin, Jetpack Compose, JavaScript, Three.js.

Минимальная версия SDK: 31

Three.js: 0.160.0 

Данный комплекс скриптов разработан для интеграции в мобильное приложение (через Android WebView)
и решает задачу двусторонней синхронизации отметок повреждений автомобиля между трехмерной моделью (GLB/Three.js) 
и двумерным интерактивным чертежом (SVG).

Оба компонента построены по объектно-ориентированному принципу с разделением ответственности.

Страница для просмотра 3D-моделей включает в себя:

ModelManagers: Загружает .glb файл, вычисляет его физические границы и строит карту деталей машины (partMap);

    class ModelManager {
        constructor(scene) {
            this.scene = scene;
            this.model = null;
            this.bounds = null;
            this.partMap = {};
            this.loader = new GLTFLoader();
        }

        /**
         * Поднимается вверх по дереву деталей, чтобы отбросить
         * технические названия мешей.
         */
        getPartName(object) {
            let current = object;
            let topPart = null;

            while (current) {
                if (current === this.model || current.parent === this.scene || current.parent === null) break;
                if (current.name) {
                    const isTechnical = MaterialPresets.technicalRegex.test(current.name);
                    if (!isTechnical) {
                        topPart = current;
                        break;
                    } else if (!topPart) {
                        topPart = current;
                    }
                }
                current = current.parent;
            }

            const finalNode = topPart || object;
            return (finalNode.name === "Scene_Collection" || finalNode.name === "Scene")
                ? (object.name || "unnamed")
                : (finalNode.name || "unnamed");
        }

        /**
         * Сканирует загруженную модель, заполняет карту деталей partMap.
         */
        processModelNodes() {
            this.partMap = {};
            this.model.traverse(node => {
                if (node.isMesh && node.material) {
                    const materials = Array.isArray(node.material) ? node.material : [node.material];
                    materials.forEach(mat => {
                        if (mat.isMeshStandardMaterial) MaterialPresets.apply(node, mat);
                    });
                }

                if (node.name && node !== this.model) {
                    const lowName = node.name.toLowerCase().trim();
                    if (lowName !== "scene_collection" && lowName !== "scene") {
                        const cleanKey = lowName.replace(/[\s_]/g, '');
                        if (node.isMesh || node.type === "Group") {
                            this.partMap[cleanKey] = node;

                            if (/(carpaint|paint|material|glass)/i.test(lowName) && node.parent?.name) {
                                const parentKey = node.parent.name.toLowerCase().replace(/[\s_]/g, '');
                                if (parentKey !== "scene" && parentKey !== "scene_collection") {
                                    this.partMap[parentKey] = node.parent;
                                }
                            }
                        }
                    }
                }
            });
        }

        /**
         * Главный метод загрузки файла.
         */
        load(url, onProgress, onSuccess, onError) {
            if (this.model) this.scene.remove(this.model);

            this.loader.load(url, (gltf) => {
                this.model = gltf.scene;
                this.scene.add(this.model);
                this.model.updateMatrixWorld(true);

                const box = new Box3().setFromObject(this.model);
                this.bounds = {
                    minX: box.min.x, maxX: box.max.x,
                    minY: box.min.y, maxY: box.max.y,
                    minZ: box.min.z, maxZ: box.max.z,
                    center: box.getCenter(new Vector3()),
                    sizeX: box.max.x - box.min.x,
                    sizeY: box.max.y - box.min.y,
                    sizeZ: box.max.z - box.min.z
                };

                this.processModelNodes();
                onSuccess(this.model, this.bounds);
            }, onProgress, onError);
        }
    }

MarkerManagers: Отвечает за создание, отрисовку и удаление маркеров;

    class MarkerManager {
        constructor() {
            this.activeMarkers = [];
            this.geometry = new SphereGeometry(0.08, 12, 12);
            this.material = new MeshBasicMaterial({color: 0xff0000, toneMapped: false});
        }

        /**
         * Создает маркер в локальных координатах модели.
         */
        createMarker(model, worldPoint, id) {
            const marker = new Mesh(this.geometry, this.material);
            marker.name = "damage_marker";
            marker.userData = {markerId: id};
            marker.renderOrder = 999;

            const localPoint = worldPoint.clone();
            model.worldToLocal(localPoint);
            marker.position.copy(localPoint);

            model.add(marker);
            this.activeMarkers.push({id, parentContainer: model, markerObject: marker});
        }

        /**
         * Удаляет маркер со сцены по его ID.
         */
        removeMarker(id) {
            const index = this.activeMarkers.findIndex(m => m.id === id);
            if (index !== -1) {
                const item = this.activeMarkers[index];
                item.parentContainer.remove(item.markerObject);
                this.activeMarkers.splice(index, 1);
            }
        }

        /**
         * Полная очистка всех маркеров.
         */
        clearAll() {
            this.activeMarkers.forEach(item => {
                item.parentContainer.remove(item.markerObject);
            });
            this.activeMarkers = [];
        }

        /**
         * Отрисовка массива маркеров.
         */
        highlightMarker(model, markers) {
            if (!markers) return;
            const data = typeof markers === 'string' ? JSON.parse(markers) : markers;
            const array = Array.isArray(data) ? data : [data];

            array.forEach(m => {
                const worldPoint = new Vector3(parseFloat(m.x), parseFloat(m.y), parseFloat(m.z));
                this.createMarker(model, worldPoint, m.id || `marker_${Date.now()}_${Math.floor(Math.random() * 100)}`);
            });
        }
    }

DamageProjector: Класс, отвечающий за конфигурацию луча и его проецирование на модель;

    class DamageProjector {
        /**
         * В зависимости от ракурса рассчитывает
         * начальную точку луча и вектор его направления относительно габаритов машины.
         */
        static getRayConfiguration(damage, bounds) {
            let localX = 0, localY = 0, localZ = 0;
            let rayDirection = new Vector3();
            // Данные математические расчёты требуют доработки
            // для коректного отображения маркеров направление луча было смещено
            // по У на -0.2 для передней и задней части модели
            switch (damage.viewType) {
                case "top":
                    localZ = bounds.minZ + (1 - damage.nx_2d) * bounds.sizeZ;
                    localX = bounds.minX + damage.ny_2d * bounds.sizeX;
                    localY = bounds.maxY + 1.0;
                    rayDirection.set(0, -1, 0);
                    break;
                case "left":
                    localZ = bounds.minZ + (1 - damage.nx_2d) * bounds.sizeZ;
                    localY = bounds.minY + (1 - damage.ny_2d) * bounds.sizeY;
                    localX = bounds.maxX + 1.0;
                    rayDirection.set(-1, 0, 0);
                    break;
                case "right":
                    localZ = bounds.minZ + (1 - damage.nx_2d) * bounds.sizeZ;
                    localY = bounds.minY + damage.ny_2d * bounds.sizeY;
                    localX = bounds.minX - 1.0;
                    rayDirection.set(1, 0, 0);
                    break;
                case "front":
                    localX = bounds.minX + damage.ny_2d * bounds.sizeX;
                    localY = bounds.minY + damage.nx_2d * bounds.sizeY;
                    localZ = bounds.maxZ + 1.0;
                    rayDirection.set(0, -0.2, -1);
                    break;
                case "back":
                    localX = bounds.minX + damage.ny_2d * bounds.sizeX;
                    localY = bounds.minY + (1 - damage.nx_2d) * bounds.sizeY;
                    localZ = bounds.minZ - 1.0;
                    rayDirection.set(0, -0.2, 1);
                    break;
                default:
                    return null;
            }
            return {origin: new Vector3(localX, localY, localZ), direction: rayDirection};
        }

        /**
         * Выполняет проецирование и возвращает итоговую локальную точку.
         */
        static project(damage, modelManager) {
            const bounds = modelManager.bounds;
            const model = modelManager.model;

            const config = this.getRayConfiguration(damage, bounds);
            if (!config) return null;

            const rayOriginWorld = config.origin.clone();
            model.localToWorld(rayOriginWorld);

            const worldDirection = config.direction.clone().transformDirection(model.matrixWorld);
            const raycaster = new Raycaster(rayOriginWorld, worldDirection.normalize());

            const cleanKey = damage.partName.toLowerCase().replace(/[\s_]/g, '');
            let targetMesh = modelManager.partMap[cleanKey];

            if (!targetMesh) {
                model.traverse(node => {
                    if (!targetMesh && node.name?.toLowerCase().replace(/[\s_]/g, '').includes(cleanKey)) {
                        targetMesh = node;
                    }
                });
            }

            let finalWorldPoint = new Vector3();
            let hitFound = false;

            if (targetMesh) {
                const intersectsTarget = raycaster.intersectObjects([targetMesh], true);
                if (intersectsTarget.length > 0) {
                    finalWorldPoint.copy(intersectsTarget[0].point);
                    hitFound = true;
                }
            }

            const intersectsModel = raycaster.intersectObjects([model], true);
            if (intersectsModel.length > 0) {
                finalWorldPoint.copy(intersectsModel[0].point);
                hitFound = true;
            }

            if (!hitFound) {
                finalWorldPoint.set(config.origin.x, damage.viewType === "top"
                                    ? bounds.maxY : config.origin.y, config.origin.z);
                model.localToWorld(finalWorldPoint);
            }

            const localPoint = finalWorldPoint.clone();
            model.worldToLocal(localPoint);
            return localPoint;
        }
    }

MaterialPresets: Распределяет свойства материалов для элементов модели;

    const MaterialPresets = {
        technicalRegex: /(carpaint|body|sedan|suv|wagon|convertible|paint|material|glass|chrome|plastic|metal|default|texture|mesh|geometry|node|object|__)/i,
        glass: /(glass|window|headlight|windshield|mirror)/i,
        interior: /(seat|interior|salon|leather|plastic|dashboard|carpet)/i,
        paint: /(carpaint|paint|hood|door|fender|roof)/i,
        paintMat: /(paint|color|body)/i,
        metal: /(chrome|metal|wheel|rim|silver)/i,

        /**
         * @param {THREE.Object3D} node - Текущий узел (меш) 3D модели
         * @param {THREE.Material} mat - Материал, привязанный к этому мешу
         */
        apply(node, mat) {
            const nodeName = (node.name || "").toLowerCase();
            const matName = (mat.name || "").toLowerCase();

            if (this.glass.test(nodeName) || this.glass.test(matName)) {
                mat.roughness = 0.0;
                mat.metalness = 0.1;
                mat.transparent = true;
                mat.opacity = mat.opacity < 1.0 ? mat.opacity : 0.3;
            }
            else if (this.interior.test(nodeName) || this.interior.test(matName)) {
                mat.roughness = 0.85;
                mat.metalness = 0.0;
                if (mat.clearcoat !== undefined) mat.clearcoat = 0.0;
            }
            else if (this.paint.test(nodeName) || this.paintMat.test(matName)) {
                mat.roughness = 0.15;
                mat.metalness = 0.1;
                if (mat.clearcoat !== undefined) {
                    mat.clearcoat = 1.0;
                    mat.clearcoatRoughness = 0.05;
                }
            }
            else if (this.metal.test(nodeName) || this.metal.test(matName)) {
                mat.roughness = 0.05;
                mat.metalness = 1.0;
            }
        }
    };

При добавлении маркера нативная сторона получает объект JSON со следующей структурой:

        const eventData = {
            action: "marker_added",
            id: newMarkerId,
            partName: partName,
            x: localRootPoint.x, y: localRootPoint.y, z: localRootPoint.z,
            // Вычисляем процентное (0.0 - 1.0) положение маркера (для синхронизации обратно в 2D)
            nx: (localRootPoint.x - bounds.minX) / bounds.sizeX,
            ny: (localRootPoint.y - bounds.minY) / bounds.sizeY,
            nz: (localRootPoint.z - bounds.minZ) / bounds.sizeZ
        };

Страница для просмотра 2D моделей включает:

SVGModelManager: Отвечает за поиск деталей на SVG-панели и определение их родительских групп;

    class SVGModelManager {
        constructor(rootId) {
            this.root = document.getElementById(rootId);
        }
    
        /**
         * Ищет элемент детали на SVG по строковому имени.
         */
        findPart(partName) {
            if (!partName) return null;
            return this.root.querySelector(`[id^="${partName}"]`) ||
                this.root.querySelector(`[id*="${partName}"]`);
        }
    
        /**
         * Ищет ID детали вверх по DOM-дереву от места клика.
         */
        findPartId(targetElement) {
            let currentEl = targetElement;
            while (currentEl && currentEl !== this.root) {
                if (currentEl.id &&
                    !currentEl.id.startsWith("line-") &&
                    currentEl.id !== "lines" &&
                    !currentEl.id.startsWith("marker_")) {
                    return {id: currentEl.id, element: currentEl};
                }
                currentEl = currentEl.parentNode;
            }
            return null;
        }
    }

SVGMarkerManager: Отвечает за генерацию SVG-элементов (<g> и <circle>), их добавление в DOM и удаление;

    class SVGMarkerManager {
        constructor(containerId) {
            this.containerId = containerId;
            this.ns = "http://www.w3.org/2000/svg";
        }
    
        get container() {
            return document.getElementById(this.containerId);
        }
    
        /**
         * Создает SVG-группу с маркерами, добавляет её в контейнер маркеров
         */
        createMarker(x, y, markerId, onAttachHandlers) {
            const markerContainer = this.container;
            if (!markerContainer) return;
    
            const markerGroup = document.createElementNS(this.ns, "g");
            markerGroup.setAttribute("id", markerId);
            markerGroup.setAttribute("data-id", markerId);
            markerGroup.setAttribute("data-type", "damage_marker");
            markerGroup.setAttribute("style", "cursor: pointer;");
    
            const circle = document.createElementNS(this.ns, "circle");
            circle.setAttribute("cx", x);
            circle.setAttribute("cy", y);
            circle.setAttribute("r", "11");
            circle.setAttribute("fill", "#FF0000");
            circle.setAttribute("stroke", "#FFFFFF");
            circle.setAttribute("stroke-width", "2.5");
            circle.setAttribute("data-type", "damage_marker");
            circle.setAttribute("data-id", markerId);
    
            markerGroup.appendChild(circle);
            markerContainer.appendChild(markerGroup);
    
            if (typeof onAttachHandlers === "function") {
                onAttachHandlers(markerGroup);
                onAttachHandlers(circle);
            }
        }
    
        /**
         * Удаляет маркер из DOM по ID.
         */
        removeMarker(markerId) {
            const markerElement = document.getElementById(markerId);
            if (markerElement) {
                markerElement.parentNode.removeChild(markerElement);
            }
        }
    
        /**
         * Полная очистка всех маркеров на схеме.
         */
        clearAll() {
            const markerContainer = this.container;
            if (markerContainer) markerContainer.innerHTML = "";
        }
    
        /**
         * Отрисовка массива переданных 2D маркеров.
         */
        highlightMarker(markers, onAttachHandlers) {
            this.clearAll();
            if (!markers) return;
    
            let parsed = typeof markers === 'string' ? JSON.parse(markers) : markers;
            if (!Array.isArray(parsed)) parsed = [parsed];
    
            parsed.forEach(data => {
                if (!data || data.x === undefined || data.y === undefined) return;
                const id = data.id || `marker_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
                this.createMarker(parseFloat(data.x), parseFloat(data.y), id, onAttachHandlers);
            });
        }
    }

SVGProkector: Принимает нормализованные координаты куба (nx, ny, nz) и проецирует их.

    class SVGProjector {
        /**
         * Проецирует трехмерную точку повреждения на плоскость 2D
         * @param {Object} damage - Объект повреждения из 3D
         * @param {SVGElement} svgPart - Найденный SVG элемент детали
         */
        static project3DTo2D(damage, svgPart) {
            const parentGroup = svgPart.closest("g");
            if (!parentGroup) {
                console.warn("Parent group not found for part:", damage.partName);
                return null;
            }
    
            const groupId = parentGroup.id.toLowerCase();
            const bbox = parentGroup.getBBox();
    
            let exactX = 0;
            let exactY = 0;
    
            if (groupId.includes("top")) {
                exactX = bbox.x + (1 - damage.nz) * bbox.width;
                exactY = bbox.y + damage.nx * bbox.height;
            } else if (groupId.includes("right")) {
                exactX = bbox.x + (1 - damage.nz) * bbox.width;
                exactY = bbox.y + damage.ny * bbox.height;
            } else if (groupId.includes("left")) {
                exactX = bbox.x + (1 - damage.nz) * bbox.width;
                exactY = bbox.y + (1 - damage.ny) * bbox.height;
            } else if (groupId.includes("front")) {
                exactX = bbox.x + damage.ny * bbox.width;
                exactY = bbox.y + damage.nx * bbox.height;
    
                if (/(light|grille)/i.test(damage.partName)) {
                    exactX += 20;
                }
    
            } else if (groupId.includes("back")) {
                exactX = bbox.x + damage.ny * bbox.width;
                exactY = bbox.y + damage.nx * bbox.height;
    
                if (/(light|trunklid)/i.test(damage.partName)) {
                    exactX -= 30;
                }
    
            } else {
                console.warn("Unknown SVG view group orientation:", groupId);
                return null;
            }
    
            return {
                id: damage.id,
                x: parseFloat(exactX.toFixed(2)),
                y: parseFloat(exactY.toFixed(2))
            };
        }
    }

При добавлении маркера нативная сторона получает объект JSON со следующей структурой:

        const eventData = {
            action: "marker_added",
            id: newMarkerId,
            partName: cleanPartName,
            x: parseFloat(svgP.x.toFixed(2)),
            y: parseFloat(svgP.y.toFixed(2)),
            z: 0.0,
            nx_2d: nx_2d,
            ny_2d: ny_2d,
            viewType: viewType
        };

При удалении маркера нативная сторона получает одинаковый объект JSON вне зависимости от 2D/3D.

"action": "marker_removed", "id": targetId

Мапинг происходит за наименнованиями узлов (мешей).

Названия элементов кузова не должны содержать пробелов. Основные элементы должны называться строго в соответствии с бизнес-логикой (например: hood, fender_front_left, door_rear_right).

Иерархия SVG должна группироваться по ракурсам (<g id="view_top">, <g id="view_left"> и т.д.). ID элементов внутри групп должны частично или полностью совпадать с именами 3D-мешей (проверка через querySelector([id*="partName"])).

Прошу пересмотреть SVG файлы, а именно проследить за соответствием наименования дочерних элементов за id их группы. Например: coupe

    <g id="Coupe_Side_Right">
    	<g id="Sedan_Side_Right_00000027570810186355271850000014175151615517328526_">
    		<path id="RightQuarterPanel_00000041287781479590496500000009652507730820559496_" class="st0" d="M430.9,39.4l101.3,1.8
    			c3.9,18.7,8.7,37.1,30.9,49.5c22.3,7.2,38.7,2.9,52-7.3l49.7,7.3l25,11c-11.3,6.2-21.2,12.2-25,17.1c3.5,7.5,14.5,8.9,32.1,5.1
    			c5.1-2.6,7.9-6.2,9.7-10.2l5.3,1.2c-2.5,5.5-5.8,9.9-9.5,13.6l-39.2,7.1l-73.7,27.8c-47.4,18.8-102.3,22.2-158.6,22.7v-5.2V39.4z"
    			/>
    		<path id="RightFender_00000056393795121541653080000004126238519834882744_" class="st0" d="M256.5,127.5l12.4,0.6l6.9,5.3
    			l10.6-1.2l-9.9-9.3c-2.9-3.7-5-8-5.9-13.4c-0.5-24.8-0.1-48.9,2.4-72.7h-35.1c-1.1,27.3-13.1,44.6-33.9,54
    			c-15.1,4.9-28.8,3.8-41.4-2l-7.1,2l-17.3,4.1l7.1,9.1v2.4l95,14.4l14.4,4.3L256.5,127.5z"/>
    		<path id="RightFrontDoor_00000170279627032003430220000005870784083576744073_" class="st0" d="M286.5,132.2l-9.9-9.3
    			c-3.1-4-5.1-8.4-5.9-13.4c-0.7-24.4,0.1-48.6,2.4-72.7l170.9,2.4c-6.9,7.1-3.8,39.6-1.4,70.4l3.8,18.9l10.2,52.5
    			C406.2,181.9,326.4,175.8,286.5,132.2z"/>
    		<path id="RearLeftDoorWindow_00000101817450099241879700000005195746607150459529_" class="st1" d="M456.5,134l7.9,41.8
    			c60.2-5,88.6-19.4,105.3-41.8H456.5z"/>
    		<path id="FrontRightDoorWindow_00000131332155486989964840000017298930522735541394_" class="st1" d="M306.5,139.3v-12l126.9,6.1
    			l12.2,44.5C403.4,178.7,341.9,165.5,306.5,139.3z"/>
    		<path id="RightFrontStruts_00000107569010222324105720000012359571831990570648_" class="st0" d="M286.3,132.2
    			c46.5,31.4,85.1,46.7,144.5,48.8v5.2c-60.2,1.8-101.5-11.8-155-52.8L286.3,132.2z"/>
    		<path id="RightTailLight_00000181060058817419171560000010132741519101626296_" class="st3" d="M689.8,101.7
    			c-10.9,5.8-20.1,11.5-25,17.1c3.7,7.2,14,9.1,32.1,5.1c4.1-1.7,7.1-5.4,9.7-10.2l14.1-21.5C712,92.5,701.3,96.4,689.8,101.7z"/>
    		<path id="TrunkLid_00000140713953946529191660000013825115534490372502_" class="st0" d="M663.2,135.6l39.2-7.1
    			c3.9-3.9,7.1-8.4,9.5-13.6l-5.3-1.2l14.2-21.4l2.5,1c-2.1,7.8-3.3,15.5-3.5,23.1l3.5,3l-8.9,14.6l-52.6,8.5l-31.7,5.6L663.2,135.6
    			z"/>
    		<path id="RearBumper_00000140000888696361704660000012903417609886501014_" class="st0" d="M664.8,90.6l25,11
    			c10.4-4.7,20.7-9,31-9.5l2.5,1c2.5-5.6,5.8-10.5,10-14.7l-0.1-21.3c0,0-7.2-1.6-15.2-11.5s-25.3-10.8-25.3-10.8l-54.8-6.5
    			c-1.3,22.8-8.1,41.8-22.9,55.2L664.8,90.6z"/>
    		<path id="FrontBumper_00000098183681057904546450000017461533999279247509_" class="st0" d="M162.6,88.6
    			c-21-10.5-30.7-33.2-28.2-69.2c-56.3,0-40.8,6.1-51,8.9l-5.1,2.1c-0.5,0.3-0.5,1,0,1.2l7.1,3.1l-3.9,14.7
    			c-0.7,2.5-2.9,4.2-5.5,4.2h-2.3v8.7l4.2,2.2c1.1,0.4,1.9,1.2,2.4,2.3l14.5,31.3l6.5,1c-4.6-5-9.7-14.5-14.8-24.4l2.6-2
    			c18.2,4.7,34.5,12.1,49.1,22.1L162.6,88.6z"/>
    		<path id="LeftHeadlight_00000147902579902397956850000000902177452349873586_" class="st3" d="M145.3,106.4l-0.1-2.5l-7.1-9.1
    			c-12.6-8.7-28-16.5-49.1-22.1l-2.6,2c4.6,9.4,9.4,18,14.8,24.4L145.3,106.4L145.3,106.4z"/>
    		<path id="Windshield_00000158729311297034241490000014338102885046512552_" class="st1" d="M268.9,128.2l-12.5-0.6
    			c0,0-3.6,1.2-5,1.7c41.9,34.6,91,48.2,133.3,54.6C355.6,179.3,324.1,171.3,268.9,128.2z"/>
    		<path id="Hood_00000067959218144696547240000015771915814754952090_" class="st0" d="M254.8,125l-14.4-4.3l-95-14.4L101.3,99
    			l-6.5-1l0,0c0.9,1.8,0.1,4.1-1.9,4.8c-1,0.4-2.2,0.8-3.6,1.1c41.9,18.3,158.3,26.7,158.3,26.7l8.9-3.1L254.8,125z"/>
    		<path id="Grille_00000031907065447900344200000017955701465952260238_" class="st3" d="M78.3,64.7l1.9,2.1l14.5,31.3l0,0
    			c0.8,2.1-0.4,4.4-2.5,5l-3,0.9l0,0c-2.6,0-4.9-1.4-6.1-3.7l-0.6-1.2l-8.9-29.5c-0.5-1.7-0.6-3.6-0.3-5.4l0.3-1.9L78.3,64.7z"/>
    		<path id="FrontBumperGrille_00000070825452932856681830000015970357831597256592_" class="st3" d="M73.6,53.7h2.1
    			c2.6,0,4.9-1.7,5.7-4.2l0,0l3.9-14.7l-7.1-3.1l-4.6,21.9V53.7z"/>
    		<path id="FrontBumperGrille_00000034080067792142550700000009671443657717037498_" class="st3" d="M95.5,41.7
    			c1.4-3.6,4.7-6.1,8.6-6.3l0,0l16.7-0.5c0,0-0.6-0.5,1.2,1.9s-3,18.1-7.7,20.9c-4.6,2.9-21.8-7.6-21.8-7.6L95.5,41.7z"/>
    		<path id="RearWindshield_00000178908503597085591230000003678360566749423547_" class="st1" d="M630,148.1l22.9-4
    			c-20.4,9.4-20.4,9.6-39,16.6c-12.4,4.6-41.7,11.9-65.4,15.1l0,0C567.7,173,600.7,159.7,630,148.1z"/>
    		<path id="LeftRockerPanels_00000137810164834419567650000017248204996206650503_" class="st0" d="M525.2,41.6L418,39.1l-145.1-2.4
    			h-35.1V23.9c110.5,1.2,212.4,1.5,294.4,0v17.3L525.2,41.6z"/>
    		<path id="LeftMirrorGlass_00000181772580496089493760000015655902729729203390_" class="st0" d="M298.6,132.2
    			c0,7.4,6,13.4,13.4,13.4h7.7c0,0,6.7-2.8,6.7-9.3s-2.8-8.9-2.8-8.9h-27.2c0,0-2.2,0.2-2.3,1.8C294,130.8,298.6,132.2,298.6,132.2
    			L298.6,132.2z"/>
    		<circle id="LeftFrontTier_00000170996382404962153550000002446794775226601141_" class="st4" cx="185.3" cy="45.4" r="44.8"/>
    		<circle id="LeftFrontWheelRim_00000010994951492515713110000008031271227787549059_" class="st0" cx="185.9" cy="45.4" r="34.9"/>
    		<circle id="LeftRearTier_00000046314126893423300120000001992725078342534317_" class="st4" cx="583.7" cy="46.5" r="44.8"/>
    		<circle id="LeftRearWheelRim_00000085246019999151385690000003611630426601869998_" class="st0" cx="584.2" cy="46.6" r="34.9"/>
    		<path id="LeftFrontDoorHandle_00000026122892822228664220000008869946904300360614_" class="st1" d="M438.6,117.2v0.3
    			c0,1.6-1.3,2.8-2.8,2.8h-20.9c-1.6,0-2.8-1.3-2.8-2.8v-0.3c0-1.6,1.3-2.8,2.8-2.8h20.9C437.3,114.4,438.5,115.6,438.6,117.2z"/>
    	</g>
    </g>

Наименование дочерниих элементов не соотвествуют id группы. Группа отвечающая за правую часть автомобиля содержит в себе элементы используемые для левой части.

Мостом для получения данных со страницы в Android является веб-интерфейс:

    class WebAppInterface(private val onMessageReceived: (String) -> Unit) {
        @JavascriptInterface
        fun postMessage(message: String) {
            onMessageReceived(message)
        }
    }

Список повреждений отправляеться с нативной стороны, предварительно переведя список данных в json:

    LaunchedEffect(jsArray, isPageLoaded) {
        if (isPageLoaded) {
            val json = jsArray.replace("'", "\\'")
            webView.evaluateJavascript("load3DDamageToSVG($json);", null)
        }
    }

Загрузка модели осущствляется следующим образом:

    LaunchedEffect(fileName) {
            isPageLoaded = false
            val svgContent = context.assets.open(fileName).bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                svgContent,
                "text/html",
                "utf-8",
                null
            )
    }

Соответственно для 3D моделей:

    LaunchedEffect(model, damageIds, isPageLoaded) {
        if (isPageLoaded && model.isNotEmpty()) {
            try {
                val virtualUrl = "https://appassets.androidplatform.net/assets/3D/$model.glb"

                webView.evaluateJavascript("loadModel('$virtualUrl');", null)

                val json = jsArray.replace("'", "\\'")
                webView.evaluateJavascript("load2DDamageTo3D($json);", null)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

Математическая модель проекции использует плоские Bounding Box (getBBox), из-за чего на экстремально изогнутых элементах возникает пространственная погрешность. Для компенсации этих искажений в классах Projector применяются хардкодные поправочные коэффициенты под конкретную геометрию модели автомобиля.

    rayDirection.set(0, -0.2, -1);

    if (/(light|trunklid)/i.test(damage.partName)) {
        exactX -= 30;
    }

    if (/(light|grille)/i.test(damage.partName)) {
        exactX += 20;
    }
